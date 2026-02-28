from ink.py.model import asset_pb2 as _asset_pb2
from google.protobuf.internal import containers as _containers
from google.protobuf.internal import enum_type_wrapper as _enum_type_wrapper
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from typing import ClassVar as _ClassVar, Iterable as _Iterable, Mapping as _Mapping, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class InventoryAction(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
    __slots__ = ()
    INVENTORY_ACTION_UNSPECIFIED: _ClassVar[InventoryAction]
    EQUIP: _ClassVar[InventoryAction]
    UNEQUIP: _ClassVar[InventoryAction]
    ADD: _ClassVar[InventoryAction]
    REMOVE: _ClassVar[InventoryAction]
    USE: _ClassVar[InventoryAction]
    DROP: _ClassVar[InventoryAction]

class LoadPriority(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
    __slots__ = ()
    LOAD_PRIORITY_UNSPECIFIED: _ClassVar[LoadPriority]
    IMMEDIATE: _ClassVar[LoadPriority]
    PRELOAD: _ClassVar[LoadPriority]
    LAZY: _ClassVar[LoadPriority]
INVENTORY_ACTION_UNSPECIFIED: InventoryAction
EQUIP: InventoryAction
UNEQUIP: InventoryAction
ADD: InventoryAction
REMOVE: InventoryAction
USE: InventoryAction
DROP: InventoryAction
LOAD_PRIORITY_UNSPECIFIED: LoadPriority
IMMEDIATE: LoadPriority
PRELOAD: LoadPriority
LAZY: LoadPriority

class AssetEvent(_message.Message):
    __slots__ = ("session_id", "event_type", "asset", "timestamp")
    SESSION_ID_FIELD_NUMBER: _ClassVar[int]
    EVENT_TYPE_FIELD_NUMBER: _ClassVar[int]
    ASSET_FIELD_NUMBER: _ClassVar[int]
    TIMESTAMP_FIELD_NUMBER: _ClassVar[int]
    session_id: str
    event_type: str
    asset: _asset_pb2.AssetRef
    timestamp: int
    def __init__(self, session_id: _Optional[str] = ..., event_type: _Optional[str] = ..., asset: _Optional[_Union[_asset_pb2.AssetRef, _Mapping]] = ..., timestamp: _Optional[int] = ...) -> None: ...

class InventoryChangeEvent(_message.Message):
    __slots__ = ("session_id", "action", "emoji", "asset", "timestamp", "item_name")
    SESSION_ID_FIELD_NUMBER: _ClassVar[int]
    ACTION_FIELD_NUMBER: _ClassVar[int]
    EMOJI_FIELD_NUMBER: _ClassVar[int]
    ASSET_FIELD_NUMBER: _ClassVar[int]
    TIMESTAMP_FIELD_NUMBER: _ClassVar[int]
    ITEM_NAME_FIELD_NUMBER: _ClassVar[int]
    session_id: str
    action: str
    emoji: str
    asset: _asset_pb2.AssetRef
    timestamp: int
    item_name: str
    def __init__(self, session_id: _Optional[str] = ..., action: _Optional[str] = ..., emoji: _Optional[str] = ..., asset: _Optional[_Union[_asset_pb2.AssetRef, _Mapping]] = ..., timestamp: _Optional[int] = ..., item_name: _Optional[str] = ...) -> None: ...

class InkTagEvent(_message.Message):
    __slots__ = ("session_id", "tags", "knot", "timestamp", "resolved_assets")
    SESSION_ID_FIELD_NUMBER: _ClassVar[int]
    TAGS_FIELD_NUMBER: _ClassVar[int]
    KNOT_FIELD_NUMBER: _ClassVar[int]
    TIMESTAMP_FIELD_NUMBER: _ClassVar[int]
    RESOLVED_ASSETS_FIELD_NUMBER: _ClassVar[int]
    session_id: str
    tags: _containers.RepeatedScalarFieldContainer[str]
    knot: str
    timestamp: int
    resolved_assets: _containers.RepeatedCompositeFieldContainer[_asset_pb2.AssetRef]
    def __init__(self, session_id: _Optional[str] = ..., tags: _Optional[_Iterable[str]] = ..., knot: _Optional[str] = ..., timestamp: _Optional[int] = ..., resolved_assets: _Optional[_Iterable[_Union[_asset_pb2.AssetRef, _Mapping]]] = ...) -> None: ...

class AssetLoadRequest(_message.Message):
    __slots__ = ("session_id", "asset", "priority", "timestamp")
    SESSION_ID_FIELD_NUMBER: _ClassVar[int]
    ASSET_FIELD_NUMBER: _ClassVar[int]
    PRIORITY_FIELD_NUMBER: _ClassVar[int]
    TIMESTAMP_FIELD_NUMBER: _ClassVar[int]
    session_id: str
    asset: _asset_pb2.AssetRef
    priority: str
    timestamp: int
    def __init__(self, session_id: _Optional[str] = ..., asset: _Optional[_Union[_asset_pb2.AssetRef, _Mapping]] = ..., priority: _Optional[str] = ..., timestamp: _Optional[int] = ...) -> None: ...

class VoiceSynthRequest(_message.Message):
    __slots__ = ("session_id", "text", "voice_ref", "language", "timestamp")
    SESSION_ID_FIELD_NUMBER: _ClassVar[int]
    TEXT_FIELD_NUMBER: _ClassVar[int]
    VOICE_REF_FIELD_NUMBER: _ClassVar[int]
    LANGUAGE_FIELD_NUMBER: _ClassVar[int]
    TIMESTAMP_FIELD_NUMBER: _ClassVar[int]
    session_id: str
    text: str
    voice_ref: _asset_pb2.VoiceRef
    language: str
    timestamp: int
    def __init__(self, session_id: _Optional[str] = ..., text: _Optional[str] = ..., voice_ref: _Optional[_Union[_asset_pb2.VoiceRef, _Mapping]] = ..., language: _Optional[str] = ..., timestamp: _Optional[int] = ...) -> None: ...
