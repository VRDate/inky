from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from typing import ClassVar as _ClassVar, Optional as _Optional

DESCRIPTOR: _descriptor.FileDescriptor

class InkEvent(_message.Message):
    __slots__ = ("uid", "summary", "description", "dt_start", "dt_end", "category", "location", "status")
    UID_FIELD_NUMBER: _ClassVar[int]
    SUMMARY_FIELD_NUMBER: _ClassVar[int]
    DESCRIPTION_FIELD_NUMBER: _ClassVar[int]
    DT_START_FIELD_NUMBER: _ClassVar[int]
    DT_END_FIELD_NUMBER: _ClassVar[int]
    CATEGORY_FIELD_NUMBER: _ClassVar[int]
    LOCATION_FIELD_NUMBER: _ClassVar[int]
    STATUS_FIELD_NUMBER: _ClassVar[int]
    uid: str
    summary: str
    description: str
    dt_start: str
    dt_end: str
    category: str
    location: str
    status: str
    def __init__(self, uid: _Optional[str] = ..., summary: _Optional[str] = ..., description: _Optional[str] = ..., dt_start: _Optional[str] = ..., dt_end: _Optional[str] = ..., category: _Optional[str] = ..., location: _Optional[str] = ..., status: _Optional[str] = ...) -> None: ...
