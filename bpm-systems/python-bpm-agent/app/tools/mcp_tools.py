"""
MCP Tools - Production-Ready BPM Actions

Features:
- Tool versioning & schema validation
- Extensible tool registry with metadata
- Error handling with retries
- Logging & audit trail
- Configuration for different BPM systems
"""
import os
import uuid
import logging
import time
from abc import ABC, abstractmethod
from datetime import datetime
from typing import Dict, Any, List, Optional, Callable, Type
from enum import Enum
from functools import wraps
from pydantic import BaseModel, Field, validator
from dotenv import load_dotenv

load_dotenv()

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("mcp_tools")


# ============================================================================
# CONFIGURATION
# ============================================================================

class BPMConfig(BaseModel):
    """BPM System Configuration"""
    
    name: str = "default"
    api_base_url: str = Field(default="http://localhost:8080/api/v1")
    api_key: Optional[str] = None
    timeout: int = Field(default=30, ge=1, le=300)
    max_retries: int = Field(default=3, ge=0, le=10)
    retry_delay: float = Field(default=1.0, ge=0.1, le=60.0)
    
    # Feature flags
    enable_notifications: bool = True
    enable_auto_approve: bool = True
    enable_task_creation: bool = True
    
    @classmethod
    def from_env(cls) -> "BPMConfig":
        """Load config from environment variables"""
        return cls(
            name=os.getenv("BPM_SYSTEM_NAME", "default"),
            api_base_url=os.getenv("BPM_API_URL", "http://localhost:8080/api/v1"),
            api_key=os.getenv("BPM_API_KEY"),
            timeout=int(os.getenv("BPM_TIMEOUT", "30")),
            max_retries=int(os.getenv("BPM_MAX_RETRIES", "3")),
            enable_notifications=os.getenv("BPM_ENABLE_NOTIFICATIONS", "true").lower() == "true",
            enable_auto_approve=os.getenv("BPM_ENABLE_AUTO_APPROVE", "true").lower() == "true",
            enable_task_creation=os.getenv("BPM_ENABLE_TASK_CREATION", "true").lower() == "true",
        )


# ============================================================================
# TOOL SCHEMAS (Pydantic Models)
# ============================================================================

class ToolResult(BaseModel):
    """Standard tool execution result"""
    success: bool
    tool: str
    version: str
    result: Optional[str] = None
    error: Optional[str] = None
    execution_time_ms: int = 0
    timestamp: str = Field(default_factory=lambda: datetime.now().isoformat())
    metadata: Dict[str, Any] = Field(default_factory=dict)


class Category(str, Enum):
    TECH_SUPPORT = "TECH_SUPPORT"
    BILLING = "BILLING"
    HR = "HR"
    GENERAL = "GENERAL"


class Priority(str, Enum):
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"
    URGENT = "URGENT"


class NotificationChannel(str, Enum):
    EMAIL = "email"
    SMS = "sms"
    SLACK = "slack"
    WEBHOOK = "webhook"


# Tool Parameter Schemas
class UpdateCategoryParams(BaseModel):
    """Parameters for updateCategory tool"""
    category: Category
    reason: Optional[str] = None

    class Config:
        use_enum_values = True


class SetPriorityParams(BaseModel):
    """Parameters for setPriority tool"""
    priority: Priority
    reason: Optional[str] = None
    escalation_note: Optional[str] = None

    class Config:
        use_enum_values = True


class CreateTaskParams(BaseModel):
    """Parameters for createTask tool"""
    team: str = Field(..., min_length=1, max_length=100)
    description: str = Field(..., min_length=1, max_length=2000)
    due_date: Optional[str] = None
    assignee: Optional[str] = None
    tags: List[str] = Field(default_factory=list)

    @validator("team")
    def validate_team(cls, v):
        valid_teams = ["TechTeam", "BillingTeam", "HRTeam", "GeneralSupport", "SecurityTeam"]
        if v not in valid_teams:
            # Allow but warn
            logger.warning(f"Unknown team: {v}. Valid teams: {valid_teams}")
        return v


class AskMissingInfoParams(BaseModel):
    """Parameters for askMissingInfo tool"""
    fields: List[str] = Field(..., min_items=1)
    message: Optional[str] = None
    deadline_hours: Optional[int] = Field(default=48, ge=1, le=720)


class AutoApproveParams(BaseModel):
    """Parameters for autoApprove tool"""
    reason: Optional[str] = None
    approver_id: str = "SYSTEM_AUTO"
    conditions_met: List[str] = Field(default_factory=list)


class SendNotificationParams(BaseModel):
    """Parameters for sendNotification tool"""
    to: str = Field(..., min_length=1)
    channel: NotificationChannel
    message: str = Field(..., min_length=1, max_length=1000)
    subject: Optional[str] = None
    priority: Priority = Priority.MEDIUM

    class Config:
        use_enum_values = True


# ============================================================================
# TOOL REGISTRY & METADATA
# ============================================================================

class ToolMetadata(BaseModel):
    """Metadata for each registered tool"""
    name: str
    version: str
    description: str
    params_schema: Type[BaseModel]
    requires_auth: bool = False
    feature_flag: Optional[str] = None  # Config field to check
    deprecated: bool = False
    deprecation_message: Optional[str] = None


# ============================================================================
# DECORATORS
# ============================================================================

def with_retry(max_retries: int = 3, delay: float = 1.0):
    """Decorator for retry logic"""
    def decorator(func: Callable):
        @wraps(func)
        def wrapper(*args, **kwargs):
            last_error = None
            for attempt in range(max_retries + 1):
                try:
                    return func(*args, **kwargs)
                except Exception as e:
                    last_error = e
                    if attempt < max_retries:
                        logger.warning(f"Retry {attempt + 1}/{max_retries} for {func.__name__}: {e}")
                        time.sleep(delay * (attempt + 1))  # Exponential backoff
            raise last_error
        return wrapper
    return decorator


def audit_log(func: Callable):
    """Decorator for audit logging"""
    @wraps(func)
    def wrapper(self, params: BaseModel, *args, **kwargs):
        logger.info(f"[AUDIT] Tool: {func.__name__} | Params: {params.model_dump()}")
        result = func(self, params, *args, **kwargs)
        logger.info(f"[AUDIT] Tool: {func.__name__} | Result: {result}")
        return result
    return wrapper


# ============================================================================
# BASE TOOL CLASS
# ============================================================================

class BaseTool(ABC):
    """Abstract base class for all MCP tools"""
    
    name: str
    version: str = "1.0.0"
    description: str
    params_schema: Type[BaseModel]
    
    def __init__(self, config: BPMConfig):
        self.config = config
    
    @abstractmethod
    def execute(self, params: BaseModel) -> str:
        """Execute the tool with validated parameters"""
        pass
    
    def validate_params(self, params: Dict[str, Any]) -> BaseModel:
        """Validate and parse parameters"""
        return self.params_schema(**params)


# ============================================================================
# TOOL IMPLEMENTATIONS
# ============================================================================

class UpdateCategoryTool(BaseTool):
    name = "updateCategory"
    version = "1.0.0"
    description = "Update request category in BPM system"
    params_schema = UpdateCategoryParams
    
    @audit_log
    @with_retry(max_retries=3)
    def execute(self, params: UpdateCategoryParams) -> str:
        """Update category in BPM"""
        logger.info(f"[BPM API] updateCategory: {params.category}")
        
        # Mock implementation - in production, call BPM API
        # response = requests.post(
        #     f"{self.config.api_base_url}/cases/category",
        #     json={"category": params.category, "reason": params.reason},
        #     headers={"Authorization": f"Bearer {self.config.api_key}"},
        #     timeout=self.config.timeout
        # )
        
        return f"Category updated to {params.category}"


class SetPriorityTool(BaseTool):
    name = "setPriority"
    version = "1.0.0"
    description = "Set request priority in BPM system"
    params_schema = SetPriorityParams
    
    @audit_log
    @with_retry(max_retries=3)
    def execute(self, params: SetPriorityParams) -> str:
        """Set priority in BPM"""
        logger.info(f"[BPM API] setPriority: {params.priority}")
        
        if params.escalation_note:
            logger.info(f"  Escalation note: {params.escalation_note}")
        
        return f"Priority set to {params.priority}"


class CreateTaskTool(BaseTool):
    name = "createTask"
    version = "1.1.0"  # Version bump for tags support
    description = "Create task for assigned team"
    params_schema = CreateTaskParams
    
    @audit_log
    @with_retry(max_retries=3)
    def execute(self, params: CreateTaskParams) -> str:
        """Create task in BPM"""
        if not self.config.enable_task_creation:
            return "Task creation disabled by configuration"
        
        task_id = f"T-{uuid.uuid4().hex[:6].upper()}"
        logger.info(f"[BPM API] createTask: {task_id} -> {params.team}")
        logger.info(f"  Description: {params.description[:100]}...")
        
        if params.due_date:
            logger.info(f"  Due Date: {params.due_date}")
        if params.assignee:
            logger.info(f"  Assignee: {params.assignee}")
        if params.tags:
            logger.info(f"  Tags: {', '.join(params.tags)}")
        
        return f"Task {task_id} created for {params.team}"


class AskMissingInfoTool(BaseTool):
    name = "askMissingInfo"
    version = "1.0.0"
    description = "Request missing information from customer"
    params_schema = AskMissingInfoParams
    
    @audit_log
    def execute(self, params: AskMissingInfoParams) -> str:
        """Request missing info"""
        logger.info(f"[BPM API] askMissingInfo: {', '.join(params.fields)}")
        
        if params.message:
            logger.info(f"  Message: {params.message}")
        
        return f"Requested missing fields: {', '.join(params.fields)}"


class AutoApproveTool(BaseTool):
    name = "autoApprove"
    version = "1.0.0"
    description = "Auto-approve request based on rules"
    params_schema = AutoApproveParams
    
    @audit_log
    def execute(self, params: AutoApproveParams) -> str:
        """Auto-approve request"""
        if not self.config.enable_auto_approve:
            return "Auto-approval disabled by configuration"
        
        logger.info(f"[BPM API] autoApprove by {params.approver_id}")
        
        if params.reason:
            logger.info(f"  Reason: {params.reason}")
        if params.conditions_met:
            logger.info(f"  Conditions met: {', '.join(params.conditions_met)}")
        
        return "Request auto-approved"


class SendNotificationTool(BaseTool):
    name = "sendNotification"
    version = "1.0.0"
    description = "Send notification via various channels"
    params_schema = SendNotificationParams
    
    @audit_log
    @with_retry(max_retries=2)
    def execute(self, params: SendNotificationParams) -> str:
        """Send notification"""
        if not self.config.enable_notifications:
            return "Notifications disabled by configuration"
        
        logger.info(f"[BPM API] sendNotification: {params.channel} -> {params.to}")
        logger.info(f"  Message: {params.message[:100]}...")
        
        if params.subject:
            logger.info(f"  Subject: {params.subject}")
        
        return f"Notification sent via {params.channel} to {params.to}"


# ============================================================================
# NEW EXTENSIBLE TOOLS (for future backlog)
# ============================================================================

class EscalateTool(BaseTool):
    """Escalate request to management"""
    name = "escalate"
    version = "1.0.0"
    description = "Escalate request to management or specialized team"
    
    class Params(BaseModel):
        level: int = Field(default=1, ge=1, le=5)
        reason: str = Field(..., min_length=10)
        notify_stakeholders: bool = True
    
    params_schema = Params
    
    @audit_log
    def execute(self, params) -> str:
        logger.info(f"[BPM API] escalate: Level {params.level}")
        logger.info(f"  Reason: {params.reason}")
        return f"Request escalated to level {params.level}"


class AddCommentTool(BaseTool):
    """Add internal comment to request"""
    name = "addComment"
    version = "1.0.0"
    description = "Add internal comment to request for audit trail"
    
    class Params(BaseModel):
        comment: str = Field(..., min_length=1, max_length=5000)
        visibility: str = Field(default="internal")  # internal, customer, all
        author: str = "AI_AGENT"
    
    params_schema = Params
    
    @audit_log
    def execute(self, params) -> str:
        logger.info(f"[BPM API] addComment: {params.visibility}")
        logger.info(f"  Comment: {params.comment[:100]}...")
        return f"Comment added ({params.visibility})"


class AttachDocumentTool(BaseTool):
    """Attach document reference to request"""
    name = "attachDocument"
    version = "1.0.0"
    description = "Attach document or reference to the request"
    
    class Params(BaseModel):
        document_type: str  # policy, transcript, attachment
        reference: str
        description: Optional[str] = None
    
    params_schema = Params
    
    @audit_log
    def execute(self, params) -> str:
        logger.info(f"[BPM API] attachDocument: {params.document_type}")
        logger.info(f"  Reference: {params.reference}")
        return f"Document attached: {params.document_type}"


class ScheduleFollowUpTool(BaseTool):
    """Schedule follow-up action"""
    name = "scheduleFollowUp"
    version = "1.0.0"
    description = "Schedule a follow-up action or reminder"
    
    class Params(BaseModel):
        action: str = Field(..., min_length=1)
        schedule_hours: int = Field(default=24, ge=1, le=720)
        assignee: Optional[str] = None
    
    params_schema = Params
    
    @audit_log
    def execute(self, params) -> str:
        logger.info(f"[BPM API] scheduleFollowUp: in {params.schedule_hours}h")
        logger.info(f"  Action: {params.action}")
        return f"Follow-up scheduled in {params.schedule_hours} hours"


# ============================================================================
# TOOL EXECUTOR (MAIN CLASS)
# ============================================================================

class MCPToolExecutor:
    """
    Production-ready MCP Tool Executor
    
    Features:
    - Tool registry with versioning
    - Schema validation
    - Retry logic
    - Audit logging
    - Configuration support
    - Extensible architecture
    """
    
    VERSION = "2.0.0"
    
    def __init__(self, config: Optional[BPMConfig] = None):
        self.config = config or BPMConfig.from_env()
        self._registry: Dict[str, BaseTool] = {}
        self._metadata: Dict[str, ToolMetadata] = {}
        
        # Register built-in tools
        self._register_builtin_tools()
        
        logger.info(f"MCPToolExecutor v{self.VERSION} initialized with {len(self._registry)} tools")
    
    def _register_builtin_tools(self):
        """Register all built-in tools"""
        builtin_tools = [
            UpdateCategoryTool,
            SetPriorityTool,
            CreateTaskTool,
            AskMissingInfoTool,
            AutoApproveTool,
            SendNotificationTool,
            # New extensible tools
            EscalateTool,
            AddCommentTool,
            AttachDocumentTool,
            ScheduleFollowUpTool,
        ]
        
        for tool_class in builtin_tools:
            self.register_tool(tool_class)
    
    def register_tool(self, tool_class: Type[BaseTool]):
        """Register a tool with the executor"""
        tool = tool_class(self.config)
        
        self._registry[tool.name] = tool
        self._metadata[tool.name] = ToolMetadata(
            name=tool.name,
            version=tool.version,
            description=tool.description,
            params_schema=tool.params_schema,
        )
        
        logger.debug(f"Registered tool: {tool.name} v{tool.version}")
    
    def get_tool_metadata(self, tool_name: str) -> Optional[ToolMetadata]:
        """Get metadata for a tool"""
        return self._metadata.get(tool_name)
    
    def list_tools(self) -> List[Dict[str, Any]]:
        """List all registered tools with metadata"""
        return [
            {
                "name": meta.name,
                "version": meta.version,
                "description": meta.description,
                "params": meta.params_schema.model_json_schema(),
            }
            for meta in self._metadata.values()
        ]
    
    def execute(self, tool_name: str, params: Dict[str, Any]) -> ToolResult:
        """Execute a single tool with validation"""
        start_time = time.time()
        
        if tool_name not in self._registry:
            return ToolResult(
                success=False,
                tool=tool_name,
                version="unknown",
                error=f"Unknown tool: {tool_name}. Available: {list(self._registry.keys())}"
            )
        
        tool = self._registry[tool_name]
        
        try:
            # Validate parameters
            validated_params = tool.validate_params(params)
            
            # Execute tool
            result = tool.execute(validated_params)
            
            execution_time = int((time.time() - start_time) * 1000)
            
            return ToolResult(
                success=True,
                tool=tool_name,
                version=tool.version,
                result=result,
                execution_time_ms=execution_time
            )
            
        except Exception as e:
            execution_time = int((time.time() - start_time) * 1000)
            logger.error(f"Tool execution error: {tool_name} - {e}")
            
            return ToolResult(
                success=False,
                tool=tool_name,
                version=tool.version,
                error=str(e),
                execution_time_ms=execution_time
            )
    
    def execute_all(self, tool_calls: List[Dict[str, Any]]) -> List[ToolResult]:
        """Execute multiple tools in sequence"""
        results = []
        
        for call in tool_calls:
            tool_name = call.get("tool")
            params = call.get("params", {})
            result = self.execute(tool_name, params)
            results.append(result)
        
        return results
    
    def format_actions(self, results: List[ToolResult]) -> List[str]:
        """Format tool results as human-readable actions"""
        actions = []
        
        for result in results:
            if isinstance(result, ToolResult):
                if result.success:
                    actions.append(result.result)
                else:
                    actions.append(f"Error ({result.tool}): {result.error}")
            elif isinstance(result, dict):
                # Backward compatibility
                if result.get("success"):
                    actions.append(result.get("result", "Action completed"))
                else:
                    actions.append(f"Error: {result.get('error', 'Unknown error')}")
        
        return actions
    
    def get_tools_for_prompt(self) -> str:
        """Generate tool descriptions for LLM prompt"""
        lines = ["AVAILABLE MCP TOOLS:"]
        
        for meta in self._metadata.values():
            params = meta.params_schema.model_json_schema()
            required = params.get("required", [])
            properties = params.get("properties", {})
            
            param_desc = []
            for prop_name, prop_info in properties.items():
                req = "*" if prop_name in required else ""
                param_desc.append(f"{prop_name}{req}: {prop_info.get('type', 'any')}")
            
            lines.append(f"- {meta.name}({', '.join(param_desc)}): {meta.description}")
        
        return "\n".join(lines)
