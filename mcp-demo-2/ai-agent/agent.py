"""
AI Agent - MCP Client (JSON-RPC 2.0) + LLM Integration

Bu agent MCP Server ile JSON-RPC 2.0 protokolü üzerinden iletişim kurar.

JSON-RPC Methods:
- tools/list: Tool listesi al
- tools/call: Tool çalıştır

Port: 5003
"""

import os
import json
import requests
from flask import Flask, request, jsonify, Response
from flask_cors import CORS
from dotenv import load_dotenv
import google.generativeai as genai

load_dotenv(dotenv_path='../.env')

app = Flask(__name__)
CORS(app)

# MCP Server adresi - JSON-RPC endpoint
MCP_SERVER = 'http://localhost:5001'

genai.configure(api_key=os.getenv('GEMINI_API_KEY'))
model = genai.GenerativeModel('gemini-2.5-flash')

# PENDING ACTIONS STORAGE
pending_actions = {}

# JSON-RPC request ID counter
request_id_counter = 0

def get_next_request_id():
    """JSON-RPC request ID üret"""
    global request_id_counter
    request_id_counter += 1
    return request_id_counter


def safe_parse_llm_json(raw_text):
    """
    LLM yanıtını güvenli şekilde JSON parse et.
    Parse başarısız olursa None döndür.
    """
    try:
        return json.loads(raw_text.strip())
    except Exception:
        return None


def get_tools_from_mcp():
    """
    MCP Server'dan Tool Listesini Al (JSON-RPC 2.0)

    JSON-RPC Request:
    {
        "jsonrpc": "2.0",
        "method": "tools/list",
        "id": 1
    }

    Returns:
        list: Tool tanımları listesi
    """
    request_payload = {
        "jsonrpc": "2.0",
        "method": "tools/list",
        "id": get_next_request_id()
    }
    response = requests.post(f'{MCP_SERVER}/rpc', json=request_payload, timeout=10)
    response_json = response.json()
    return response_json.get('result', {}).get('tools', []), request_payload, response_json


def execute_tool(tool_name, arguments):
    """
    MCP Server'da Tool Çalıştır (JSON-RPC 2.0)

    JSON-RPC Request:
    {
        "jsonrpc": "2.0",
        "method": "tools/call",
        "params": {
            "name": "create_qos_session",
            "arguments": {"qosProfile": "QOS_L"}
        },
        "id": 2
    }

    Returns:
        dict: taskId ve status içeren result
    """

    request_payload = {
        "jsonrpc": "2.0",
        "method": "tools/call",
        "params": {
            "name": tool_name,
            "arguments": arguments
        },
        "id": get_next_request_id()
    }
    response = requests.post(f'{MCP_SERVER}/rpc', json=request_payload, timeout=15)
    response_json = response.json()
    return response_json.get('result', {}), request_payload, response_json


def check_user_intent(user_message, pending_offer):
    """
    LLM ile kullanıcının niyetini belirle (onay/red/diğer)
    Hardcoded keyword yerine doğal dil anlayışı
    
    Returns:
        str: 'confirm', 'reject', veya 'other'
    """
    prompt = f"""Kullanıcıya bir teklif sunuldu. Şimdi kullanıcının yanıtını değerlendir.

    SUNULAN TEKLİF: "{pending_offer}"
    KULLANICININ YANITI: "{user_message}"

    KARAR VER:
    - "confirm": Kullanıcı TEKLİFİ kabul ediyor (evet, tamam, olur, yap, onaylıyorum gibi)
    - "reject": Kullanıcı TEKLİFİ reddediyor (hayır, istemiyorum, iptal, vazgeç gibi)
    - "other": Kullanıcı YENİ BİR İSTEK yapıyor veya SORU soruyor
    
    ÖNEMLİ: 
    - "internet hızımı arttır" gibi genel istekler → "other" (yeni istek)
    - Sadece "evet", "tamam", "yap" gibi DOĞRUDAN ONAY → "confirm"
    - Kararsızlık veya soru → "other"

    SADECE tek kelime yanıt ver: confirm, reject veya other
    """
    response = model.generate_content(prompt)
    intent = response.text.strip().lower()
    
    if 'confirm' in intent:
        return 'confirm'
    elif 'reject' in intent:
        return 'reject'
    else:
        return 'other'


def ask_llm(user_message, tools):
    """
    LLM'e Mesaj + Tool'ları Gönder, Karar Al
    """
    tools_description = json.dumps(tools, indent=2)

    prompt = f"""Sen bir network asistanısın. Kullanıcının isteğini analiz et ve uygun tool'u seç.

    MEVCUT TOOLS:
    {tools_description}

    KULLANICI MESAJI: {user_message}

    GÖREV:
    1. Kullanıcının ne istediğini anla
    2. Uygun tool'u seç
    3. Parametreleri belirle
    4. Neden bu tool'u seçtiğini açıkla (reasoning)

    ÖNEMLİ - KULLANICI DOSTU DİL (offer_message ve message için):
    ASLA teknik terim kullanma! Kullanıcı bunları bilmiyor:
    - "QoS", "QOS_S", "QOS_M", "QOS_L", "QOS_E" YASAK → sadece hız değeri kullan
    - "CAMARA", "API", "session", "oturum", "profil" YASAK
    
    PLAN LİSTESİ İÇİN USER-FRIENDLY İSİMLER:
    - 50 Mbps → "Temel Paket" (100 TL)
    - 200 Mbps → "Standart Paket" (200 TL)  
    - 700 Mbps → "Hızlı Paket" (300 TL)
    - 1500 Mbps → "Ultra Paket" (400 TL)

    Örnek YANLIŞ offer_message: "Hızlı Paket - 700 Mbps (300 TL/ay)"
    Örnek DOĞRU offer_message: "Hızlı Paket - 700 Mbps (300 TL/ay) ile hızınızı artırabiliriz. Onaylıyor musunuz?"

    OFFER_MESSAGE HER ZAMAN TAM CÜMLE OLMALI VE SORU İLE BİTMELİ!

    YANIT FORMAT (JSON):
    {{
    "understood": "kullanıcının isteğinin özeti",
    "reasoning": "Bu tool'u seçmemin nedeni: ... (detaylı açıklama)",
    "tool": "tool_name",
    "parameters": {{}},
    "offer_message": "TAM CÜMLE teklif mesajı (Türkçe, teknik terim YOK, SORU ile bitmeli)"
    }}

    Eğer hiçbir tool uygun değilse:
    {{
    "understood": "...",
    "reasoning": "Neden uygun tool bulunamadı açıklaması",
    "tool": null,
    "message": "açıklama mesajı (Türkçe, teknik terim YOK)"
    }}
    """

    response = model.generate_content(prompt)
    response_text = response.text

    raw_response = response_text

    if '```json' in response_text:
        response_text = response_text.split('```json')[1].split('```')[0]
    elif '```' in response_text:
        response_text = response_text.split('```')[1].split('```')[0]

    parsed = safe_parse_llm_json(response_text)

    if parsed is None:
        return {
            "_raw_response": raw_response,
            "_prompt": prompt,
            "tool": None,
            "message": "Üzgünüm, isteğinizi şu anda işleyemedim. Lütfen tekrar dener misiniz?"
        }

    parsed['_raw_response'] = raw_response
    parsed['_prompt'] = prompt

    return parsed


@app.route('/chat', methods=['POST'])
def chat():
    """
    Ana Chat Endpoint'i
    """
    data = request.json
    user_message = data.get('message', '')
    session_id = data.get('sessionId', 'default')

    # Pending action varsa LLM ile niyet kontrolü
    user_intent = None
    if session_id in pending_actions:
        pending_offer = pending_actions[session_id].get('offer_text', '')
        user_intent = check_user_intent(user_message, pending_offer)
        
        # 'other' ise eski pending action'ı sil, yeni istek olarak işlenecek
        if user_intent == 'other':
            pending_actions.pop(session_id, None)

    is_confirmation = user_intent == 'confirm'
    is_rejection = user_intent == 'reject'

    # =========================================================================
    # SENARYO 1: Kullanıcı onayladı - Pending action'ı execute et
    # =========================================================================
    if is_confirmation:
        action = pending_actions.pop(session_id)

        # JSON-RPC ile tools/call
        result, mcp_call_request, mcp_call_response = execute_tool(action['tool'], action['parameters'])

        return jsonify({
            'type': 'executing',
            'confirmation_detected': True,  
            'confirmed_action': f"{action['tool']} onaylandı",
            'tool': action['tool'],
            'taskId': result.get('taskId'),
            'sseUrl': f'http://localhost:5003/sse/{result.get("taskId")}',
            'mcp_tool_call_request': mcp_call_request,
            'mcp_tool_call_response': mcp_call_response
        })

    # =========================================================================
    # SENARYO 2: Kullanıcı reddetti - İptal et
    # =========================================================================
    if is_rejection:
        pending_actions.pop(session_id)
        return jsonify({
            'type': 'cancelled',
            'message': 'İşlem iptal edildi.'
        })

    # =========================================================================
    # SENARYO 3: Normal mesaj - Tool seç, offer sun
    # =========================================================================

    # JSON-RPC ile tools/list
    try:
        tools, mcp_tools_list_request, mcp_tools_list_response = get_tools_from_mcp()
    except Exception:
        return jsonify({
            'type': 'message',
            'message': 'Şu anda servislerle bağlantı kurulamıyor. Lütfen biraz sonra tekrar deneyin.'
        })

    llm_response = ask_llm(user_message, tools)

    response_data = {
        'tools_obtained': tools,
        'mcp_tools_list_request': mcp_tools_list_request,
        'mcp_tools_list_response': mcp_tools_list_response,
        'llm_input': llm_response.get('_prompt'),
        'llm_raw_response': llm_response.get('_raw_response'),
        'llm_reasoning': llm_response.get('reasoning'),
        'understood': llm_response.get('understood')
    }

    if not llm_response.get('tool'):
        response_data['type'] = 'message'
        response_data['message'] = llm_response.get('message', 'Bu işlem için uygun bir araç bulunamadı.')
        return jsonify(response_data)

    selected_tool = llm_response['tool']
    parameters = llm_response['parameters']

    READ_TOOLS = ['get_network_context', 'get_qos_status']

    if selected_tool in READ_TOOLS:
        # JSON-RPC ile tools/call - READ tool direkt execute
        result, mcp_call_request, mcp_call_response = execute_tool(selected_tool, parameters)

        response_data['type'] = 'executing'
        response_data['tool'] = selected_tool
        response_data['taskId'] = result.get('taskId')
        response_data['sseUrl'] = f'http://localhost:5003/sse/{result.get("taskId")}'
        response_data['mcp_tool_call_request'] = mcp_call_request
        response_data['mcp_tool_call_response'] = mcp_call_response
        return jsonify(response_data)

    # WRITE tool - Kullanıcıya offer sun
    offer_text = llm_response.get('offer_message', f"{selected_tool} çalıştırılacak. Onaylıyor musunuz?")

    pending_actions[session_id] = {
        'tool': selected_tool,
        'parameters': parameters,
        'offer_text': offer_text  # LLM niyet kontrolü için
    }

    response_data['type'] = 'offer'
    response_data['tool'] = selected_tool
    response_data['parameters'] = parameters
    response_data['offer_text'] = offer_text

    return jsonify(response_data)


@app.route('/sse/<task_id>')
def proxy_sse(task_id):
    """
    SSE Proxy Endpoint
    UI ←──SSE──→ Agent ←──SSE──→ MCP Server
    """
    def generate():
        print(f'[AGENT] SSE proxy started for task {task_id}')

        try:
            response = requests.get(f'{MCP_SERVER}/sse/{task_id}', stream=True, timeout=30)
            for line in response.iter_lines():
                if line:
                    decoded = line.decode('utf-8')
                    print(f'[AGENT] SSE event received: {decoded[:50]}...')
                    yield decoded + '\n\n'
        except Exception as err:
            error_payload = json.dumps({
                "type": "error",
                "message": f"SSE bağlantı hatası: {str(err)}"
            })
            yield f"data: {error_payload}\n\n"

        print(f'[AGENT] SSE proxy ended for task {task_id}')

    return Response(
        generate(),
        mimetype='text/event-stream',
        headers={
            'Cache-Control': 'no-cache',
            'Connection': 'keep-alive'
        }
    )


@app.route('/health')
def health():
    """Health check endpoint"""
    return jsonify({'status': 'ok', 'service': 'ai-agent', 'protocol': 'json-rpc-2.0'})


if __name__ == '__main__':
    print('[AGENT] Starting AI Agent on http://localhost:5003')
    print(f'[AGENT] MCP Server: {MCP_SERVER}/rpc (JSON-RPC 2.0)')
    print(f'[AGENT] Gemini API Key: {"configured" if os.getenv("GEMINI_API_KEY") else "MISSING!"}')
    app.run(host='0.0.0.0', port=5003, debug=True)
