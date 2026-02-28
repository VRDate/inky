from google.protobuf.internal import containers as _containers
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from typing import ClassVar as _ClassVar, Iterable as _Iterable, Mapping as _Mapping, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class JsonRpcRequest(_message.Message):
    __slots__ = ("jsonrpc", "id", "method", "params_json")
    JSONRPC_FIELD_NUMBER: _ClassVar[int]
    ID_FIELD_NUMBER: _ClassVar[int]
    METHOD_FIELD_NUMBER: _ClassVar[int]
    PARAMS_JSON_FIELD_NUMBER: _ClassVar[int]
    jsonrpc: str
    id: str
    method: str
    params_json: str
    def __init__(self, jsonrpc: _Optional[str] = ..., id: _Optional[str] = ..., method: _Optional[str] = ..., params_json: _Optional[str] = ...) -> None: ...

class JsonRpcResponse(_message.Message):
    __slots__ = ("jsonrpc", "id", "result_json", "error")
    JSONRPC_FIELD_NUMBER: _ClassVar[int]
    ID_FIELD_NUMBER: _ClassVar[int]
    RESULT_JSON_FIELD_NUMBER: _ClassVar[int]
    ERROR_FIELD_NUMBER: _ClassVar[int]
    jsonrpc: str
    id: str
    result_json: str
    error: JsonRpcError
    def __init__(self, jsonrpc: _Optional[str] = ..., id: _Optional[str] = ..., result_json: _Optional[str] = ..., error: _Optional[_Union[JsonRpcError, _Mapping]] = ...) -> None: ...

class JsonRpcError(_message.Message):
    __slots__ = ("code", "message", "data_json")
    CODE_FIELD_NUMBER: _ClassVar[int]
    MESSAGE_FIELD_NUMBER: _ClassVar[int]
    DATA_JSON_FIELD_NUMBER: _ClassVar[int]
    code: int
    message: str
    data_json: str
    def __init__(self, code: _Optional[int] = ..., message: _Optional[str] = ..., data_json: _Optional[str] = ...) -> None: ...

class McpServerInfo(_message.Message):
    __slots__ = ("name", "version")
    NAME_FIELD_NUMBER: _ClassVar[int]
    VERSION_FIELD_NUMBER: _ClassVar[int]
    name: str
    version: str
    def __init__(self, name: _Optional[str] = ..., version: _Optional[str] = ...) -> None: ...

class McpCapabilities(_message.Message):
    __slots__ = ("tools",)
    TOOLS_FIELD_NUMBER: _ClassVar[int]
    tools: McpToolCapability
    def __init__(self, tools: _Optional[_Union[McpToolCapability, _Mapping]] = ...) -> None: ...

class McpToolCapability(_message.Message):
    __slots__ = ("list_changed",)
    LIST_CHANGED_FIELD_NUMBER: _ClassVar[int]
    list_changed: bool
    def __init__(self, list_changed: bool = ...) -> None: ...

class McpInitializeResult(_message.Message):
    __slots__ = ("protocol_version", "capabilities", "server_info")
    PROTOCOL_VERSION_FIELD_NUMBER: _ClassVar[int]
    CAPABILITIES_FIELD_NUMBER: _ClassVar[int]
    SERVER_INFO_FIELD_NUMBER: _ClassVar[int]
    protocol_version: str
    capabilities: McpCapabilities
    server_info: McpServerInfo
    def __init__(self, protocol_version: _Optional[str] = ..., capabilities: _Optional[_Union[McpCapabilities, _Mapping]] = ..., server_info: _Optional[_Union[McpServerInfo, _Mapping]] = ...) -> None: ...

class McpToolInfo(_message.Message):
    __slots__ = ("name", "description", "input_schema_json")
    NAME_FIELD_NUMBER: _ClassVar[int]
    DESCRIPTION_FIELD_NUMBER: _ClassVar[int]
    INPUT_SCHEMA_JSON_FIELD_NUMBER: _ClassVar[int]
    name: str
    description: str
    input_schema_json: str
    def __init__(self, name: _Optional[str] = ..., description: _Optional[str] = ..., input_schema_json: _Optional[str] = ...) -> None: ...

class McpToolsListResult(_message.Message):
    __slots__ = ("tools",)
    TOOLS_FIELD_NUMBER: _ClassVar[int]
    tools: _containers.RepeatedCompositeFieldContainer[McpToolInfo]
    def __init__(self, tools: _Optional[_Iterable[_Union[McpToolInfo, _Mapping]]] = ...) -> None: ...

class McpContentBlock(_message.Message):
    __slots__ = ("type", "text")
    TYPE_FIELD_NUMBER: _ClassVar[int]
    TEXT_FIELD_NUMBER: _ClassVar[int]
    type: str
    text: str
    def __init__(self, type: _Optional[str] = ..., text: _Optional[str] = ...) -> None: ...

class McpToolResult(_message.Message):
    __slots__ = ("content", "is_error")
    CONTENT_FIELD_NUMBER: _ClassVar[int]
    IS_ERROR_FIELD_NUMBER: _ClassVar[int]
    content: _containers.RepeatedCompositeFieldContainer[McpContentBlock]
    is_error: bool
    def __init__(self, content: _Optional[_Iterable[_Union[McpContentBlock, _Mapping]]] = ..., is_error: bool = ...) -> None: ...
