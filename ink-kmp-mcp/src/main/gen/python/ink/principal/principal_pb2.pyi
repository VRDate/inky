from google.protobuf.internal import containers as _containers
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from typing import ClassVar as _ClassVar, Iterable as _Iterable, Optional as _Optional

DESCRIPTOR: _descriptor.FileDescriptor

class InkPrincipal(_message.Message):
    __slots__ = ("id", "name", "roles", "is_llm", "email")
    ID_FIELD_NUMBER: _ClassVar[int]
    NAME_FIELD_NUMBER: _ClassVar[int]
    ROLES_FIELD_NUMBER: _ClassVar[int]
    IS_LLM_FIELD_NUMBER: _ClassVar[int]
    EMAIL_FIELD_NUMBER: _ClassVar[int]
    id: str
    name: str
    roles: _containers.RepeatedScalarFieldContainer[str]
    is_llm: bool
    email: str
    def __init__(self, id: _Optional[str] = ..., name: _Optional[str] = ..., roles: _Optional[_Iterable[str]] = ..., is_llm: bool = ..., email: _Optional[str] = ...) -> None: ...

class InkPrincipalInfo(_message.Message):
    __slots__ = ("id", "display_name", "email", "role", "is_llm", "folder_path", "mcp_uri")
    ID_FIELD_NUMBER: _ClassVar[int]
    DISPLAY_NAME_FIELD_NUMBER: _ClassVar[int]
    EMAIL_FIELD_NUMBER: _ClassVar[int]
    ROLE_FIELD_NUMBER: _ClassVar[int]
    IS_LLM_FIELD_NUMBER: _ClassVar[int]
    FOLDER_PATH_FIELD_NUMBER: _ClassVar[int]
    MCP_URI_FIELD_NUMBER: _ClassVar[int]
    id: str
    display_name: str
    email: str
    role: str
    is_llm: bool
    folder_path: str
    mcp_uri: str
    def __init__(self, id: _Optional[str] = ..., display_name: _Optional[str] = ..., email: _Optional[str] = ..., role: _Optional[str] = ..., is_llm: bool = ..., folder_path: _Optional[str] = ..., mcp_uri: _Optional[str] = ...) -> None: ...
