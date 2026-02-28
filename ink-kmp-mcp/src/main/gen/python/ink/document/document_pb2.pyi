from ink.table import table_pb2 as _table_pb2
from google.protobuf.internal import containers as _containers
from google.protobuf.internal import enum_type_wrapper as _enum_type_wrapper
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from typing import ClassVar as _ClassVar, Iterable as _Iterable, Mapping as _Mapping, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class EditorMode(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
    __slots__ = ()
    EDITOR_MODE_UNSPECIFIED: _ClassVar[EditorMode]
    EDIT: _ClassVar[EditorMode]
    PLAY: _ClassVar[EditorMode]
EDITOR_MODE_UNSPECIFIED: EditorMode
EDIT: EditorMode
PLAY: EditorMode

class InkFile(_message.Message):
    __slots__ = ("name", "ink_source", "header_level")
    NAME_FIELD_NUMBER: _ClassVar[int]
    INK_SOURCE_FIELD_NUMBER: _ClassVar[int]
    HEADER_LEVEL_FIELD_NUMBER: _ClassVar[int]
    name: str
    ink_source: str
    header_level: int
    def __init__(self, name: _Optional[str] = ..., ink_source: _Optional[str] = ..., header_level: _Optional[int] = ...) -> None: ...

class ParseResult(_message.Message):
    __slots__ = ("files", "tables")
    FILES_FIELD_NUMBER: _ClassVar[int]
    TABLES_FIELD_NUMBER: _ClassVar[int]
    files: _containers.RepeatedCompositeFieldContainer[InkFile]
    tables: _containers.RepeatedCompositeFieldContainer[_table_pb2.MdTable]
    def __init__(self, files: _Optional[_Iterable[_Union[InkFile, _Mapping]]] = ..., tables: _Optional[_Iterable[_Union[_table_pb2.MdTable, _Mapping]]] = ...) -> None: ...
