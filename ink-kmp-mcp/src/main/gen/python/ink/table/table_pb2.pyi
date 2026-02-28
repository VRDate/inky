from google.protobuf.internal import containers as _containers
from google.protobuf.internal import enum_type_wrapper as _enum_type_wrapper
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from typing import ClassVar as _ClassVar, Iterable as _Iterable, Mapping as _Mapping, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class CellType(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
    __slots__ = ()
    STRING: _ClassVar[CellType]
    INT: _ClassVar[CellType]
    FLOAT: _ClassVar[CellType]
    BOOL: _ClassVar[CellType]
    FORMULA: _ClassVar[CellType]
    EMOJI: _ClassVar[CellType]
    FAKER: _ClassVar[CellType]
STRING: CellType
INT: CellType
FLOAT: CellType
BOOL: CellType
FORMULA: CellType
EMOJI: CellType
FAKER: CellType

class MdCell(_message.Message):
    __slots__ = ("value", "formula", "evaluated", "type")
    VALUE_FIELD_NUMBER: _ClassVar[int]
    FORMULA_FIELD_NUMBER: _ClassVar[int]
    EVALUATED_FIELD_NUMBER: _ClassVar[int]
    TYPE_FIELD_NUMBER: _ClassVar[int]
    value: str
    formula: str
    evaluated: str
    type: CellType
    def __init__(self, value: _Optional[str] = ..., formula: _Optional[str] = ..., evaluated: _Optional[str] = ..., type: _Optional[_Union[CellType, str]] = ...) -> None: ...

class MdRow(_message.Message):
    __slots__ = ("cells",)
    class CellsEntry(_message.Message):
        __slots__ = ("key", "value")
        KEY_FIELD_NUMBER: _ClassVar[int]
        VALUE_FIELD_NUMBER: _ClassVar[int]
        key: str
        value: MdCell
        def __init__(self, key: _Optional[str] = ..., value: _Optional[_Union[MdCell, _Mapping]] = ...) -> None: ...
    CELLS_FIELD_NUMBER: _ClassVar[int]
    cells: _containers.MessageMap[str, MdCell]
    def __init__(self, cells: _Optional[_Mapping[str, MdCell]] = ...) -> None: ...

class MdTable(_message.Message):
    __slots__ = ("name", "columns", "rows")
    NAME_FIELD_NUMBER: _ClassVar[int]
    COLUMNS_FIELD_NUMBER: _ClassVar[int]
    ROWS_FIELD_NUMBER: _ClassVar[int]
    name: str
    columns: _containers.RepeatedScalarFieldContainer[str]
    rows: _containers.RepeatedCompositeFieldContainer[MdRow]
    def __init__(self, name: _Optional[str] = ..., columns: _Optional[_Iterable[str]] = ..., rows: _Optional[_Iterable[_Union[MdRow, _Mapping]]] = ...) -> None: ...
