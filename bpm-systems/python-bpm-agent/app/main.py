"""BPM Intelligent Intake Agent - FastAPI Application"""
from fastapi import FastAPI, HTTPException, UploadFile, File
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from fastapi.responses import HTMLResponse, FileResponse
import uuid
from datetime import datetime
from pathlib import Path
import shutil
import requests
import os

from app.models import IntakeRequest, IntakeResponse, AgentDecision
from app.llm.agent import IntakeAgent
from app.tools.mcp_tools import MCPToolExecutor
from app.test_scenarios import TEST_SCENARIOS
from app.call_scenarios import CALL_SCENARIOS
from app.speech.transcriber import SpeechTranscriber, CallProcessor
from app.microservices import router as microservices_router

app = FastAPI(
    title="BPM Intelligent Intake Agent",
    description="AI-powered request intake and routing system",
    version="0.1.0"
)

# CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Static files
app.mount("/static", StaticFiles(directory="static"), name="static")

# Include microservices router
app.include_router(microservices_router)

# Global instances
agent = IntakeAgent()
tool_executor = MCPToolExecutor()
speech_transcriber = SpeechTranscriber(provider="mock")  # mock mode for demo
call_processor = CallProcessor(speech_transcriber)


@app.get("/", response_class=HTMLResponse)
async def root():
    """Serve main UI"""
    html_file = Path("templates/index.html")
    if html_file.exists():
        return FileResponse("templates/index.html")
    return {
        "service": "BPM Intelligent Intake Agent",
        "status": "running",
        "version": "0.1.0",
        "timestamp": datetime.now().isoformat()
    }


@app.get("/api/scenarios")
async def get_scenarios():
    """Test senaryolarını döndür"""
    return {"scenarios": TEST_SCENARIOS}


@app.get("/api/stats")
async def get_stats():
    """Sistem istatistikleri"""
    try:
        from app.rag.retriever import QdrantRetriever
        retriever = QdrantRetriever()

        try:
            # Simple count query instead of get_collection (version compatibility)
            result = retriever.client.count(retriever.collection_name)
            documents_count = result.count if hasattr(result, 'count') else 0
            qdrant_status = "ok"
        except Exception as e:
            documents_count = 0
            qdrant_status = "error"
            print(f"Qdrant error: {e}")

        return {
            "api_status": "running",
            "qdrant_status": qdrant_status,
            "documents_count": documents_count,
            "collection_name": retriever.collection_name,
            "timestamp": datetime.now().isoformat()
        }
    except Exception as e:
        return {
            "api_status": "error",
            "error": str(e),
            "timestamp": datetime.now().isoformat()
        }


@app.post("/api/intake", response_model=IntakeResponse)
async def process_intake(request: IntakeRequest):
    """
    Talep işleme endpoint'i

    Flow:
    1. Agent talebi analiz eder (LLM + RAG)
    2. Karar alınır (kategori, öncelik, tool'lar)
    3. Tool'lar execute edilir (BPM aksiyonları)
    4. Response döner
    """
    try:
        # 1. Agent analizi
        print(f"\n{'='*60}")
        print(f"[INTAKE] New request from {request.source}")
        print(f"[INTAKE] Text: {request.text}")
        print(f"{'='*60}\n")

        decision = agent.analyze(request)

        print(f"\n[AGENT DECISION]")
        print(f"  Intent: {decision.intent}")
        print(f"  Category: {decision.category}")
        print(f"  Priority: {decision.priority}")
        print(f"  Auto Approve: {decision.auto_approve}")
        print(f"  Missing Fields: {decision.missing_fields}")
        print(f"  Tool Calls: {len(decision.tool_calls)}")
        print(f"  Reasoning: {decision.reasoning}\n")

        # 2. Tool execution
        tool_calls_dict = [
            {"tool": tc.tool, "params": tc.params}
            for tc in decision.tool_calls
        ]

        results = tool_executor.execute_all(tool_calls_dict)
        actions_taken = tool_executor.format_actions(results)

        print(f"\n[ACTIONS TAKEN]")
        for action in actions_taken:
            print(f"  ✓ {action}")
        print(f"\n{'='*60}\n")

        # 3. Generate case ID
        case_id = f"C-{uuid.uuid4().hex[:8].upper()}"

        # 4. Response
        response = IntakeResponse(
            case_id=case_id,
            intent=decision.intent,
            category=decision.category,
            priority=decision.priority,
            missing_fields=decision.missing_fields,
            auto_approve=decision.auto_approve,
            actions_taken=actions_taken,
            reasoning=decision.reasoning
        )

        return response

    except Exception as e:
        print(f"\n[ERROR] {str(e)}\n")
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/api/health")
async def health_check():
    """Detaylı health check"""
    try:
        # Qdrant connection check
        from app.rag.retriever import QdrantRetriever
        retriever = QdrantRetriever()
        stats = retriever.get_stats()

        return {
            "status": "healthy",
            "services": {
                "api": "ok",
                "qdrant": "ok",
                "collection": stats.get("collection_name"),
                "documents": stats.get("documents_count", 0),
                "rag_version": stats.get("version", "unknown")
            },
            "timestamp": datetime.now().isoformat()
        }
    except Exception as e:
        return {
            "status": "unhealthy",
            "error": str(e),
            "timestamp": datetime.now().isoformat()
        }


@app.post("/api/test/analyze")
async def test_analyze(request: IntakeRequest):
    """Test endpoint - sadece analiz, tool execution yok"""
    try:
        decision = agent.analyze(request)
        return decision.model_dump()
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/api/call/scenarios")
async def get_call_scenarios():
    """Phone call senaryolarını döndür"""
    # Add audio URLs to scenarios
    scenarios_with_audio = []
    for scenario in CALL_SCENARIOS:
        s = scenario.copy()
        s["audio_url"] = f"/static/audio/{scenario['audio_file']}"
        scenarios_with_audio.append(s)
    return {"scenarios": scenarios_with_audio}


@app.post("/api/call/upload")
async def upload_audio(
    file: UploadFile = File(...),
    customer_id: str = "CUST-UPLOAD"
):
    """
    Real audio file upload & processing

    Flow:
    1. Save uploaded audio file
    2. Transcribe with Whisper (real speech-to-text)
    3. Analyze with sentiment + agent
    4. Execute BPM actions
    5. Return results

    Accepts: .mp3, .wav, .m4a, .ogg
    """
    try:
        # Validate file type
        allowed_extensions = [".mp3", ".wav", ".m4a", ".ogg", ".webm"]
        file_ext = Path(file.filename).suffix.lower()

        if file_ext not in allowed_extensions:
            raise HTTPException(
                status_code=400,
                detail=f"Invalid file type. Allowed: {', '.join(allowed_extensions)}"
            )

        # Save uploaded file
        upload_dir = Path("static/uploads")
        upload_dir.mkdir(parents=True, exist_ok=True)

        file_id = uuid.uuid4().hex[:8]
        saved_path = upload_dir / f"{file_id}_{file.filename}"

        with saved_path.open("wb") as buffer:
            shutil.copyfileobj(file.file, buffer)

        print(f"\n{'='*60}")
        print(f"[AUDIO UPLOAD] File: {file.filename}")
        print(f"[AUDIO UPLOAD] Saved: {saved_path}")
        print(f"{'='*60}\n")

        # Real Whisper transcription
        from app.speech.whisper_transcriber import get_whisper_transcriber

        transcriber = get_whisper_transcriber()
        transcription = transcriber.transcribe(str(saved_path), language="tr")

        print(f"[TRANSCRIPTION]")
        print(f"  Text: {transcription['text'][:100]}...")
        print(f"  Duration: {transcription['duration']:.1f}s")
        print(f"  Speaking Rate: {transcription['metadata']['speaking_rate']} wpm")

        # Create intake request
        intake_request = IntakeRequest(
            source="phone",
            text=transcription["text"],
            customer_id=customer_id,
            sentiment="NEUTRAL"  # Will be analyzed by agent
        )

        # Process with agent
        decision = agent.analyze(intake_request)

        print(f"\n[AGENT DECISION]")
        print(f"  Category: {decision.category}")
        print(f"  Priority: {decision.priority}")

        # Execute tools
        tool_calls_dict = [
            {"tool": tc.tool, "params": tc.params}
            for tc in decision.tool_calls
        ]

        results = tool_executor.execute_all(tool_calls_dict)
        actions_taken = tool_executor.format_actions(results)

        print(f"\n[ACTIONS TAKEN]")
        for action in actions_taken:
            print(f"  ✓ {action}")
        print(f"\n{'='*60}\n")

        # Generate case ID
        case_id = f"C-UPLOAD-{file_id.upper()}"

        # Response with transcription
        response = IntakeResponse(
            case_id=case_id,
            intent=decision.intent,
            category=decision.category,
            priority=decision.priority,
            missing_fields=decision.missing_fields,
            auto_approve=decision.auto_approve,
            actions_taken=actions_taken,
            reasoning=decision.reasoning
        )

        response_dict = response.model_dump()
        response_dict["transcription"] = {
            "text": transcription["text"],
            "confidence": transcription["confidence"],
            "duration": transcription["duration"],
            "speaking_rate": transcription["metadata"]["speaking_rate"],
            "repetitions": transcription["metadata"]["repetitions"]
        }
        response_dict["uploaded_file"] = file.filename

        # Clean up uploaded file (optional)
        # saved_path.unlink()

        return response_dict

    except Exception as e:
        print(f"\n[ERROR] {str(e)}\n")
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/api/call/process")
async def process_call(call_data: dict):
    """
    Phone call processing endpoint

    Simulates:
    1. Speech-to-Text transcription
    2. Sentiment analysis (with speech metadata)
    3. Intent detection & routing
    4. BPM actions

    Args:
        call_data: {
            "scenario_id": int,  # Mock scenario ID
            "customer_id": str
        }

    Returns:
        IntakeResponse with transcription metadata
    """
    try:
        scenario_id = call_data.get("scenario_id")
        customer_id = call_data.get("customer_id", "CUST-UNKNOWN")

        # Find scenario
        scenario = next((s for s in CALL_SCENARIOS if s["id"] == scenario_id), None)
        if not scenario:
            raise HTTPException(status_code=404, detail=f"Scenario {scenario_id} not found")

        print(f"\n{'='*60}")
        print(f"[CALL PROCESSING] Scenario: {scenario['name']}")
        print(f"[CALL PROCESSING] Customer: {customer_id}")
        print(f"{'='*60}\n")

        # Simulate transcription
        transcription = scenario["transcription"]

        print(f"[TRANSCRIPTION]")
        print(f"  Text: {transcription['text'][:100]}...")
        print(f"  Confidence: {transcription['confidence']}")
        print(f"  Duration: {transcription['duration']}s")
        print(f"  Speaking Rate: {transcription['metadata']['speaking_rate']} wpm")
        print(f"  Pitch Variance: {transcription['metadata']['pitch_variance']}")

        # Create intake request with speech metadata
        intake_request = IntakeRequest(
            source="phone",
            text=transcription["text"],
            customer_id=customer_id,
            sentiment="NEUTRAL"  # Will be overridden by advanced sentiment
        )

        # Process with agent (includes sentiment analysis)
        decision = agent.analyze(intake_request)

        print(f"\n[AGENT DECISION]")
        print(f"  Intent: {decision.intent}")
        print(f"  Category: {decision.category}")
        print(f"  Priority: {decision.priority}")
        print(f"  Auto Approve: {decision.auto_approve}")

        # Execute tools
        tool_calls_dict = [
            {"tool": tc.tool, "params": tc.params}
            for tc in decision.tool_calls
        ]

        results = tool_executor.execute_all(tool_calls_dict)
        actions_taken = tool_executor.format_actions(results)

        print(f"\n[ACTIONS TAKEN]")
        for action in actions_taken:
            print(f"  ✓ {action}")
        print(f"\n{'='*60}\n")

        # Generate case ID
        case_id = f"C-CALL-{uuid.uuid4().hex[:8].upper()}"

        # Response with call metadata
        response = IntakeResponse(
            case_id=case_id,
            intent=decision.intent,
            category=decision.category,
            priority=decision.priority,
            missing_fields=decision.missing_fields,
            auto_approve=decision.auto_approve,
            actions_taken=actions_taken,
            reasoning=decision.reasoning
        )

        # Add call metadata to response
        response_dict = response.model_dump()
        response_dict["call_metadata"] = {
            "transcription_confidence": transcription["confidence"],
            "call_duration": transcription["duration"],
            "speaking_rate": transcription["metadata"]["speaking_rate"],
            "pitch_variance": transcription["metadata"]["pitch_variance"],
            "emotion_markers": transcription["metadata"].get("emotion_markers", []),
            "repetitions": transcription["metadata"].get("repetitions", [])
        }

        return response_dict

    except Exception as e:
        print(f"\n[ERROR] {str(e)}\n")
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/api/flowable/start-process")
async def start_flowable_process(request: IntakeRequest):
    """
    Start Flowable BPM process from text or audio request

    This endpoint:
    1. Analyzes request with AI Agent
    2. Starts Flowable process instance
    3. Returns both AI decision and Flowable process ID
    """
    try:
        # 1. AI Agent analysis
        print(f"\n{'='*60}")
        print(f"[FLOWABLE INTEGRATION] New request from {request.source}")
        print(f"[FLOWABLE INTEGRATION] Customer: {request.customer_id}")
        print(f"{'='*60}\n")

        decision = agent.analyze(request)

        print(f"\n[AI DECISION]")
        print(f"  Category: {decision.category}")
        print(f"  Priority: {decision.priority}")
        print(f"  Intent: {decision.intent}\n")

        # 2. Start Flowable process
        flowable_url = os.getenv("FLOWABLE_URL", "http://localhost:8080")
        flowable_user = os.getenv("FLOWABLE_USER", "admin")
        flowable_pass = os.getenv("FLOWABLE_PASS", "test")

        process_payload = {
            "processDefinitionKey": "langgraphAIIntakeProcess",
            "variables": [
                {"name": "question", "value": request.text},
                {"name": "customerId", "value": request.customer_id},
                {"name": "source", "value": request.source},
                {"name": "agentType", "value": "auto"}
            ]
        }

        flowable_response = requests.post(
            f"{flowable_url}/flowable-task/process-api/runtime/process-instances",
            json=process_payload,
            auth=(flowable_user, flowable_pass),
            timeout=60
        )

        if flowable_response.status_code not in [200, 201]:
            print(f"[ERROR] Flowable returned {flowable_response.status_code}")
            raise HTTPException(
                status_code=500,
                detail=f"Flowable error: {flowable_response.text}"
            )

        flowable_data = flowable_response.json()
        process_id = flowable_data.get("id")

        print(f"[FLOWABLE] Process started: {process_id}\n")
        print(f"{'='*60}\n")

        # 3. Return combined response
        return {
            "success": True,
            "flowable_process_id": process_id,
            "flowable_process_url": f"{flowable_url}/flowable-task/#/processes/{process_id}",
            "ai_decision": {
                "category": decision.category,
                "priority": decision.priority,
                "intent": decision.intent,
                "auto_approve": decision.auto_approve,
                "reasoning": decision.reasoning
            },
            "message": f"Process started successfully with {decision.priority} priority"
        }

    except requests.exceptions.RequestException as e:
        print(f"\n[ERROR] Flowable connection failed: {str(e)}\n")
        raise HTTPException(
            status_code=503,
            detail=f"Could not connect to Flowable: {str(e)}"
        )
    except Exception as e:
        print(f"\n[ERROR] {str(e)}\n")
        raise HTTPException(status_code=500, detail=str(e))


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
