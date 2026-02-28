from google.protobuf.internal import containers as _containers
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from typing import ClassVar as _ClassVar, Iterable as _Iterable, Mapping as _Mapping, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class Choice(_message.Message):
    __slots__ = ("index", "text", "tags")
    INDEX_FIELD_NUMBER: _ClassVar[int]
    TEXT_FIELD_NUMBER: _ClassVar[int]
    TAGS_FIELD_NUMBER: _ClassVar[int]
    index: int
    text: str
    tags: _containers.RepeatedScalarFieldContainer[str]
    def __init__(self, index: _Optional[int] = ..., text: _Optional[str] = ..., tags: _Optional[_Iterable[str]] = ...) -> None: ...

class StoryState(_message.Message):
    __slots__ = ("text", "can_continue", "choices", "tags")
    TEXT_FIELD_NUMBER: _ClassVar[int]
    CAN_CONTINUE_FIELD_NUMBER: _ClassVar[int]
    CHOICES_FIELD_NUMBER: _ClassVar[int]
    TAGS_FIELD_NUMBER: _ClassVar[int]
    text: str
    can_continue: bool
    choices: _containers.RepeatedCompositeFieldContainer[Choice]
    tags: _containers.RepeatedScalarFieldContainer[str]
    def __init__(self, text: _Optional[str] = ..., can_continue: bool = ..., choices: _Optional[_Iterable[_Union[Choice, _Mapping]]] = ..., tags: _Optional[_Iterable[str]] = ...) -> None: ...

class CompileResult(_message.Message):
    __slots__ = ("success", "json", "errors", "warnings")
    SUCCESS_FIELD_NUMBER: _ClassVar[int]
    JSON_FIELD_NUMBER: _ClassVar[int]
    ERRORS_FIELD_NUMBER: _ClassVar[int]
    WARNINGS_FIELD_NUMBER: _ClassVar[int]
    success: bool
    json: str
    errors: _containers.RepeatedScalarFieldContainer[str]
    warnings: _containers.RepeatedScalarFieldContainer[str]
    def __init__(self, success: bool = ..., json: _Optional[str] = ..., errors: _Optional[_Iterable[str]] = ..., warnings: _Optional[_Iterable[str]] = ...) -> None: ...

class StorySession(_message.Message):
    __slots__ = ("id", "source", "state_json")
    ID_FIELD_NUMBER: _ClassVar[int]
    SOURCE_FIELD_NUMBER: _ClassVar[int]
    STATE_JSON_FIELD_NUMBER: _ClassVar[int]
    id: str
    source: str
    state_json: str
    def __init__(self, id: _Optional[str] = ..., source: _Optional[str] = ..., state_json: _Optional[str] = ...) -> None: ...
