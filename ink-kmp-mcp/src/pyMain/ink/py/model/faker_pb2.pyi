from google.protobuf.internal import containers as _containers
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from typing import ClassVar as _ClassVar, Iterable as _Iterable, Optional as _Optional

DESCRIPTOR: _descriptor.FileDescriptor

class FakerConfig(_message.Message):
    __slots__ = ("seed", "locale", "count", "level", "categories")
    SEED_FIELD_NUMBER: _ClassVar[int]
    LOCALE_FIELD_NUMBER: _ClassVar[int]
    COUNT_FIELD_NUMBER: _ClassVar[int]
    LEVEL_FIELD_NUMBER: _ClassVar[int]
    CATEGORIES_FIELD_NUMBER: _ClassVar[int]
    seed: int
    locale: str
    count: int
    level: int
    categories: _containers.RepeatedScalarFieldContainer[str]
    def __init__(self, seed: _Optional[int] = ..., locale: _Optional[str] = ..., count: _Optional[int] = ..., level: _Optional[int] = ..., categories: _Optional[_Iterable[str]] = ...) -> None: ...

class EmojiCategory(_message.Message):
    __slots__ = ("emoji", "faker_provider", "method_chain", "range_min", "range_max")
    EMOJI_FIELD_NUMBER: _ClassVar[int]
    FAKER_PROVIDER_FIELD_NUMBER: _ClassVar[int]
    METHOD_CHAIN_FIELD_NUMBER: _ClassVar[int]
    RANGE_MIN_FIELD_NUMBER: _ClassVar[int]
    RANGE_MAX_FIELD_NUMBER: _ClassVar[int]
    emoji: str
    faker_provider: str
    method_chain: str
    range_min: int
    range_max: int
    def __init__(self, emoji: _Optional[str] = ..., faker_provider: _Optional[str] = ..., method_chain: _Optional[str] = ..., range_min: _Optional[int] = ..., range_max: _Optional[int] = ...) -> None: ...
