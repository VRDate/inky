from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from typing import ClassVar as _ClassVar, Optional as _Optional

DESCRIPTOR: _descriptor.FileDescriptor

class GgufModel(_message.Message):
    __slots__ = ("id", "hugging_face_repo", "file_name", "parameters", "quantization", "architecture", "size_gb", "min_vram_gb", "jlama_compatible", "description")
    ID_FIELD_NUMBER: _ClassVar[int]
    HUGGING_FACE_REPO_FIELD_NUMBER: _ClassVar[int]
    FILE_NAME_FIELD_NUMBER: _ClassVar[int]
    PARAMETERS_FIELD_NUMBER: _ClassVar[int]
    QUANTIZATION_FIELD_NUMBER: _ClassVar[int]
    ARCHITECTURE_FIELD_NUMBER: _ClassVar[int]
    SIZE_GB_FIELD_NUMBER: _ClassVar[int]
    MIN_VRAM_GB_FIELD_NUMBER: _ClassVar[int]
    JLAMA_COMPATIBLE_FIELD_NUMBER: _ClassVar[int]
    DESCRIPTION_FIELD_NUMBER: _ClassVar[int]
    id: str
    hugging_face_repo: str
    file_name: str
    parameters: str
    quantization: str
    architecture: str
    size_gb: float
    min_vram_gb: int
    jlama_compatible: bool
    description: str
    def __init__(self, id: _Optional[str] = ..., hugging_face_repo: _Optional[str] = ..., file_name: _Optional[str] = ..., parameters: _Optional[str] = ..., quantization: _Optional[str] = ..., architecture: _Optional[str] = ..., size_gb: _Optional[float] = ..., min_vram_gb: _Optional[int] = ..., jlama_compatible: bool = ..., description: _Optional[str] = ...) -> None: ...

class ServiceDef(_message.Message):
    __slots__ = ("id", "name", "base_url", "default_model", "api_key_env", "description", "requires_api_key", "is_local", "doc_url")
    ID_FIELD_NUMBER: _ClassVar[int]
    NAME_FIELD_NUMBER: _ClassVar[int]
    BASE_URL_FIELD_NUMBER: _ClassVar[int]
    DEFAULT_MODEL_FIELD_NUMBER: _ClassVar[int]
    API_KEY_ENV_FIELD_NUMBER: _ClassVar[int]
    DESCRIPTION_FIELD_NUMBER: _ClassVar[int]
    REQUIRES_API_KEY_FIELD_NUMBER: _ClassVar[int]
    IS_LOCAL_FIELD_NUMBER: _ClassVar[int]
    DOC_URL_FIELD_NUMBER: _ClassVar[int]
    id: str
    name: str
    base_url: str
    default_model: str
    api_key_env: str
    description: str
    requires_api_key: bool
    is_local: bool
    doc_url: str
    def __init__(self, id: _Optional[str] = ..., name: _Optional[str] = ..., base_url: _Optional[str] = ..., default_model: _Optional[str] = ..., api_key_env: _Optional[str] = ..., description: _Optional[str] = ..., requires_api_key: bool = ..., is_local: bool = ..., doc_url: _Optional[str] = ...) -> None: ...
