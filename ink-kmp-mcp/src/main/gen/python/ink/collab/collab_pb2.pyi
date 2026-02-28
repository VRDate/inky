from google.protobuf.internal import containers as _containers
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from typing import ClassVar as _ClassVar, Iterable as _Iterable, Optional as _Optional

DESCRIPTOR: _descriptor.FileDescriptor

class ColabClient(_message.Message):
    __slots__ = ("client_id", "doc_id")
    CLIENT_ID_FIELD_NUMBER: _ClassVar[int]
    DOC_ID_FIELD_NUMBER: _ClassVar[int]
    client_id: str
    doc_id: str
    def __init__(self, client_id: _Optional[str] = ..., doc_id: _Optional[str] = ...) -> None: ...

class ColabDocumentInfo(_message.Message):
    __slots__ = ("doc_id", "created_at", "update_count", "client_count")
    DOC_ID_FIELD_NUMBER: _ClassVar[int]
    CREATED_AT_FIELD_NUMBER: _ClassVar[int]
    UPDATE_COUNT_FIELD_NUMBER: _ClassVar[int]
    CLIENT_COUNT_FIELD_NUMBER: _ClassVar[int]
    doc_id: str
    created_at: int
    update_count: int
    client_count: int
    def __init__(self, doc_id: _Optional[str] = ..., created_at: _Optional[int] = ..., update_count: _Optional[int] = ..., client_count: _Optional[int] = ...) -> None: ...

class EditorContext(_message.Message):
    __slots__ = ("mode", "session_id", "doc_id", "is_compiling", "errors")
    MODE_FIELD_NUMBER: _ClassVar[int]
    SESSION_ID_FIELD_NUMBER: _ClassVar[int]
    DOC_ID_FIELD_NUMBER: _ClassVar[int]
    IS_COMPILING_FIELD_NUMBER: _ClassVar[int]
    ERRORS_FIELD_NUMBER: _ClassVar[int]
    mode: str
    session_id: str
    doc_id: str
    is_compiling: bool
    errors: _containers.RepeatedScalarFieldContainer[str]
    def __init__(self, mode: _Optional[str] = ..., session_id: _Optional[str] = ..., doc_id: _Optional[str] = ..., is_compiling: bool = ..., errors: _Optional[_Iterable[str]] = ...) -> None: ...
