from ink.story import story_pb2 as _story_pb2
from google.protobuf.internal import containers as _containers
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from typing import ClassVar as _ClassVar, Iterable as _Iterable, Mapping as _Mapping, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class Breakpoint(_message.Message):
    __slots__ = ("id", "type", "target", "enabled")
    ID_FIELD_NUMBER: _ClassVar[int]
    TYPE_FIELD_NUMBER: _ClassVar[int]
    TARGET_FIELD_NUMBER: _ClassVar[int]
    ENABLED_FIELD_NUMBER: _ClassVar[int]
    id: str
    type: str
    target: str
    enabled: bool
    def __init__(self, id: _Optional[str] = ..., type: _Optional[str] = ..., target: _Optional[str] = ..., enabled: bool = ...) -> None: ...

class WatchVariable(_message.Message):
    __slots__ = ("name", "last_value", "change_count")
    NAME_FIELD_NUMBER: _ClassVar[int]
    LAST_VALUE_FIELD_NUMBER: _ClassVar[int]
    CHANGE_COUNT_FIELD_NUMBER: _ClassVar[int]
    name: str
    last_value: str
    change_count: int
    def __init__(self, name: _Optional[str] = ..., last_value: _Optional[str] = ..., change_count: _Optional[int] = ...) -> None: ...

class VisitEntry(_message.Message):
    __slots__ = ("step", "text", "choices_made", "variables_changed", "timestamp")
    STEP_FIELD_NUMBER: _ClassVar[int]
    TEXT_FIELD_NUMBER: _ClassVar[int]
    CHOICES_MADE_FIELD_NUMBER: _ClassVar[int]
    VARIABLES_CHANGED_FIELD_NUMBER: _ClassVar[int]
    TIMESTAMP_FIELD_NUMBER: _ClassVar[int]
    step: int
    text: str
    choices_made: int
    variables_changed: _containers.RepeatedScalarFieldContainer[str]
    timestamp: int
    def __init__(self, step: _Optional[int] = ..., text: _Optional[str] = ..., choices_made: _Optional[int] = ..., variables_changed: _Optional[_Iterable[str]] = ..., timestamp: _Optional[int] = ...) -> None: ...

class DebugSession(_message.Message):
    __slots__ = ("session_id", "breakpoints", "watches", "visit_log", "stepping", "step_count", "total_steps", "is_paused", "last_output")
    class WatchesEntry(_message.Message):
        __slots__ = ("key", "value")
        KEY_FIELD_NUMBER: _ClassVar[int]
        VALUE_FIELD_NUMBER: _ClassVar[int]
        key: str
        value: WatchVariable
        def __init__(self, key: _Optional[str] = ..., value: _Optional[_Union[WatchVariable, _Mapping]] = ...) -> None: ...
    SESSION_ID_FIELD_NUMBER: _ClassVar[int]
    BREAKPOINTS_FIELD_NUMBER: _ClassVar[int]
    WATCHES_FIELD_NUMBER: _ClassVar[int]
    VISIT_LOG_FIELD_NUMBER: _ClassVar[int]
    STEPPING_FIELD_NUMBER: _ClassVar[int]
    STEP_COUNT_FIELD_NUMBER: _ClassVar[int]
    TOTAL_STEPS_FIELD_NUMBER: _ClassVar[int]
    IS_PAUSED_FIELD_NUMBER: _ClassVar[int]
    LAST_OUTPUT_FIELD_NUMBER: _ClassVar[int]
    session_id: str
    breakpoints: _containers.RepeatedCompositeFieldContainer[Breakpoint]
    watches: _containers.MessageMap[str, WatchVariable]
    visit_log: _containers.RepeatedCompositeFieldContainer[VisitEntry]
    stepping: bool
    step_count: int
    total_steps: int
    is_paused: bool
    last_output: str
    def __init__(self, session_id: _Optional[str] = ..., breakpoints: _Optional[_Iterable[_Union[Breakpoint, _Mapping]]] = ..., watches: _Optional[_Mapping[str, WatchVariable]] = ..., visit_log: _Optional[_Iterable[_Union[VisitEntry, _Mapping]]] = ..., stepping: bool = ..., step_count: _Optional[int] = ..., total_steps: _Optional[int] = ..., is_paused: bool = ..., last_output: _Optional[str] = ...) -> None: ...

class WatchChange(_message.Message):
    __slots__ = ("old_value", "new_value")
    OLD_VALUE_FIELD_NUMBER: _ClassVar[int]
    NEW_VALUE_FIELD_NUMBER: _ClassVar[int]
    old_value: str
    new_value: str
    def __init__(self, old_value: _Optional[str] = ..., new_value: _Optional[str] = ...) -> None: ...

class StepResult(_message.Message):
    __slots__ = ("text", "can_continue", "choices", "tags", "hit_breakpoint", "watch_changes", "step_number", "is_paused")
    class WatchChangesEntry(_message.Message):
        __slots__ = ("key", "value")
        KEY_FIELD_NUMBER: _ClassVar[int]
        VALUE_FIELD_NUMBER: _ClassVar[int]
        key: str
        value: WatchChange
        def __init__(self, key: _Optional[str] = ..., value: _Optional[_Union[WatchChange, _Mapping]] = ...) -> None: ...
    TEXT_FIELD_NUMBER: _ClassVar[int]
    CAN_CONTINUE_FIELD_NUMBER: _ClassVar[int]
    CHOICES_FIELD_NUMBER: _ClassVar[int]
    TAGS_FIELD_NUMBER: _ClassVar[int]
    HIT_BREAKPOINT_FIELD_NUMBER: _ClassVar[int]
    WATCH_CHANGES_FIELD_NUMBER: _ClassVar[int]
    STEP_NUMBER_FIELD_NUMBER: _ClassVar[int]
    IS_PAUSED_FIELD_NUMBER: _ClassVar[int]
    text: str
    can_continue: bool
    choices: _containers.RepeatedCompositeFieldContainer[_story_pb2.Choice]
    tags: _containers.RepeatedScalarFieldContainer[str]
    hit_breakpoint: Breakpoint
    watch_changes: _containers.MessageMap[str, WatchChange]
    step_number: int
    is_paused: bool
    def __init__(self, text: _Optional[str] = ..., can_continue: bool = ..., choices: _Optional[_Iterable[_Union[_story_pb2.Choice, _Mapping]]] = ..., tags: _Optional[_Iterable[str]] = ..., hit_breakpoint: _Optional[_Union[Breakpoint, _Mapping]] = ..., watch_changes: _Optional[_Mapping[str, WatchChange]] = ..., step_number: _Optional[int] = ..., is_paused: bool = ...) -> None: ...
