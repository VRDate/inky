from google.protobuf.internal import containers as _containers
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from typing import ClassVar as _ClassVar, Iterable as _Iterable, Mapping as _Mapping, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class AssetCategory(_message.Message):
    __slots__ = ("emoji", "name", "type", "anim_set", "grip_type", "mesh_prefix", "audio_category", "unicode_group", "unicode_subgroup", "code_points", "unicode_version", "general_category", "is_game_asset")
    EMOJI_FIELD_NUMBER: _ClassVar[int]
    NAME_FIELD_NUMBER: _ClassVar[int]
    TYPE_FIELD_NUMBER: _ClassVar[int]
    ANIM_SET_FIELD_NUMBER: _ClassVar[int]
    GRIP_TYPE_FIELD_NUMBER: _ClassVar[int]
    MESH_PREFIX_FIELD_NUMBER: _ClassVar[int]
    AUDIO_CATEGORY_FIELD_NUMBER: _ClassVar[int]
    UNICODE_GROUP_FIELD_NUMBER: _ClassVar[int]
    UNICODE_SUBGROUP_FIELD_NUMBER: _ClassVar[int]
    CODE_POINTS_FIELD_NUMBER: _ClassVar[int]
    UNICODE_VERSION_FIELD_NUMBER: _ClassVar[int]
    GENERAL_CATEGORY_FIELD_NUMBER: _ClassVar[int]
    IS_GAME_ASSET_FIELD_NUMBER: _ClassVar[int]
    emoji: str
    name: str
    type: str
    anim_set: str
    grip_type: str
    mesh_prefix: str
    audio_category: str
    unicode_group: str
    unicode_subgroup: str
    code_points: _containers.RepeatedScalarFieldContainer[int]
    unicode_version: str
    general_category: str
    is_game_asset: bool
    def __init__(self, emoji: _Optional[str] = ..., name: _Optional[str] = ..., type: _Optional[str] = ..., anim_set: _Optional[str] = ..., grip_type: _Optional[str] = ..., mesh_prefix: _Optional[str] = ..., audio_category: _Optional[str] = ..., unicode_group: _Optional[str] = ..., unicode_subgroup: _Optional[str] = ..., code_points: _Optional[_Iterable[int]] = ..., unicode_version: _Optional[str] = ..., general_category: _Optional[str] = ..., is_game_asset: bool = ...) -> None: ...

class VoiceRef(_message.Message):
    __slots__ = ("character_id", "language", "flac_path")
    CHARACTER_ID_FIELD_NUMBER: _ClassVar[int]
    LANGUAGE_FIELD_NUMBER: _ClassVar[int]
    FLAC_PATH_FIELD_NUMBER: _ClassVar[int]
    character_id: str
    language: str
    flac_path: str
    def __init__(self, character_id: _Optional[str] = ..., language: _Optional[str] = ..., flac_path: _Optional[str] = ...) -> None: ...

class AssetRef(_message.Message):
    __slots__ = ("emoji", "category", "mesh_path", "anim_set_id", "voice_ref", "metadata")
    class MetadataEntry(_message.Message):
        __slots__ = ("key", "value")
        KEY_FIELD_NUMBER: _ClassVar[int]
        VALUE_FIELD_NUMBER: _ClassVar[int]
        key: str
        value: str
        def __init__(self, key: _Optional[str] = ..., value: _Optional[str] = ...) -> None: ...
    EMOJI_FIELD_NUMBER: _ClassVar[int]
    CATEGORY_FIELD_NUMBER: _ClassVar[int]
    MESH_PATH_FIELD_NUMBER: _ClassVar[int]
    ANIM_SET_ID_FIELD_NUMBER: _ClassVar[int]
    VOICE_REF_FIELD_NUMBER: _ClassVar[int]
    METADATA_FIELD_NUMBER: _ClassVar[int]
    emoji: str
    category: AssetCategory
    mesh_path: str
    anim_set_id: str
    voice_ref: VoiceRef
    metadata: _containers.ScalarMap[str, str]
    def __init__(self, emoji: _Optional[str] = ..., category: _Optional[_Union[AssetCategory, _Mapping]] = ..., mesh_path: _Optional[str] = ..., anim_set_id: _Optional[str] = ..., voice_ref: _Optional[_Union[VoiceRef, _Mapping]] = ..., metadata: _Optional[_Mapping[str, str]] = ...) -> None: ...
