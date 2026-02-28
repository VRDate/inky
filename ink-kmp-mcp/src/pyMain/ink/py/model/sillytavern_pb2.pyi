from google.protobuf.internal import containers as _containers
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from typing import ClassVar as _ClassVar, Iterable as _Iterable, Optional as _Optional

DESCRIPTOR: _descriptor.FileDescriptor

class StCharacterCard(_message.Message):
    __slots__ = ("name", "description", "personality", "scenario", "first_message", "mes_example", "system_prompt", "creator_notes", "tags")
    NAME_FIELD_NUMBER: _ClassVar[int]
    DESCRIPTION_FIELD_NUMBER: _ClassVar[int]
    PERSONALITY_FIELD_NUMBER: _ClassVar[int]
    SCENARIO_FIELD_NUMBER: _ClassVar[int]
    FIRST_MESSAGE_FIELD_NUMBER: _ClassVar[int]
    MES_EXAMPLE_FIELD_NUMBER: _ClassVar[int]
    SYSTEM_PROMPT_FIELD_NUMBER: _ClassVar[int]
    CREATOR_NOTES_FIELD_NUMBER: _ClassVar[int]
    TAGS_FIELD_NUMBER: _ClassVar[int]
    name: str
    description: str
    personality: str
    scenario: str
    first_message: str
    mes_example: str
    system_prompt: str
    creator_notes: str
    tags: _containers.RepeatedScalarFieldContainer[str]
    def __init__(self, name: _Optional[str] = ..., description: _Optional[str] = ..., personality: _Optional[str] = ..., scenario: _Optional[str] = ..., first_message: _Optional[str] = ..., mes_example: _Optional[str] = ..., system_prompt: _Optional[str] = ..., creator_notes: _Optional[str] = ..., tags: _Optional[_Iterable[str]] = ...) -> None: ...
