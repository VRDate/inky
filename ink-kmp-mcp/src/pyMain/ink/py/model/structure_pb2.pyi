from google.protobuf.internal import containers as _containers
from google.protobuf.internal import enum_type_wrapper as _enum_type_wrapper
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from typing import ClassVar as _ClassVar, Iterable as _Iterable, Mapping as _Mapping, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class SectionType(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
    __slots__ = ()
    SECTION_TYPE_UNSPECIFIED: _ClassVar[SectionType]
    KNOT: _ClassVar[SectionType]
    STITCH: _ClassVar[SectionType]
    FUNCTION: _ClassVar[SectionType]
    PREAMBLE: _ClassVar[SectionType]

class VariableType(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
    __slots__ = ()
    VARIABLE_TYPE_UNSPECIFIED: _ClassVar[VariableType]
    VAR: _ClassVar[VariableType]
    CONST: _ClassVar[VariableType]
    LIST: _ClassVar[VariableType]
    TEMP: _ClassVar[VariableType]

class DiagramMode(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
    __slots__ = ()
    DIAGRAM_MODE_UNSPECIFIED: _ClassVar[DiagramMode]
    ACTIVITY: _ClassVar[DiagramMode]
    STATE: _ClassVar[DiagramMode]
SECTION_TYPE_UNSPECIFIED: SectionType
KNOT: SectionType
STITCH: SectionType
FUNCTION: SectionType
PREAMBLE: SectionType
VARIABLE_TYPE_UNSPECIFIED: VariableType
VAR: VariableType
CONST: VariableType
LIST: VariableType
TEMP: VariableType
DIAGRAM_MODE_UNSPECIFIED: DiagramMode
ACTIVITY: DiagramMode
STATE: DiagramMode

class Section(_message.Message):
    __slots__ = ("name", "type", "start_line", "end_line", "content", "parent", "parameters", "line_count")
    NAME_FIELD_NUMBER: _ClassVar[int]
    TYPE_FIELD_NUMBER: _ClassVar[int]
    START_LINE_FIELD_NUMBER: _ClassVar[int]
    END_LINE_FIELD_NUMBER: _ClassVar[int]
    CONTENT_FIELD_NUMBER: _ClassVar[int]
    PARENT_FIELD_NUMBER: _ClassVar[int]
    PARAMETERS_FIELD_NUMBER: _ClassVar[int]
    LINE_COUNT_FIELD_NUMBER: _ClassVar[int]
    name: str
    type: SectionType
    start_line: int
    end_line: int
    content: str
    parent: str
    parameters: _containers.RepeatedScalarFieldContainer[str]
    line_count: int
    def __init__(self, name: _Optional[str] = ..., type: _Optional[_Union[SectionType, str]] = ..., start_line: _Optional[int] = ..., end_line: _Optional[int] = ..., content: _Optional[str] = ..., parent: _Optional[str] = ..., parameters: _Optional[_Iterable[str]] = ..., line_count: _Optional[int] = ...) -> None: ...

class Variable(_message.Message):
    __slots__ = ("name", "type", "initial_value", "line")
    NAME_FIELD_NUMBER: _ClassVar[int]
    TYPE_FIELD_NUMBER: _ClassVar[int]
    INITIAL_VALUE_FIELD_NUMBER: _ClassVar[int]
    LINE_FIELD_NUMBER: _ClassVar[int]
    name: str
    type: VariableType
    initial_value: str
    line: int
    def __init__(self, name: _Optional[str] = ..., type: _Optional[_Union[VariableType, str]] = ..., initial_value: _Optional[str] = ..., line: _Optional[int] = ...) -> None: ...

class DivertRef(_message.Message):
    __slots__ = ("target", "line", "column")
    TARGET_FIELD_NUMBER: _ClassVar[int]
    LINE_FIELD_NUMBER: _ClassVar[int]
    COLUMN_FIELD_NUMBER: _ClassVar[int]
    target: str
    line: int
    column: int
    def __init__(self, target: _Optional[str] = ..., line: _Optional[int] = ..., column: _Optional[int] = ...) -> None: ...

class Structure(_message.Message):
    __slots__ = ("sections", "variables", "includes", "diverts", "total_lines", "divert_count")
    SECTIONS_FIELD_NUMBER: _ClassVar[int]
    VARIABLES_FIELD_NUMBER: _ClassVar[int]
    INCLUDES_FIELD_NUMBER: _ClassVar[int]
    DIVERTS_FIELD_NUMBER: _ClassVar[int]
    TOTAL_LINES_FIELD_NUMBER: _ClassVar[int]
    DIVERT_COUNT_FIELD_NUMBER: _ClassVar[int]
    sections: _containers.RepeatedCompositeFieldContainer[Section]
    variables: _containers.RepeatedCompositeFieldContainer[Variable]
    includes: _containers.RepeatedScalarFieldContainer[str]
    diverts: _containers.RepeatedCompositeFieldContainer[DivertRef]
    total_lines: int
    divert_count: int
    def __init__(self, sections: _Optional[_Iterable[_Union[Section, _Mapping]]] = ..., variables: _Optional[_Iterable[_Union[Variable, _Mapping]]] = ..., includes: _Optional[_Iterable[str]] = ..., diverts: _Optional[_Iterable[_Union[DivertRef, _Mapping]]] = ..., total_lines: _Optional[int] = ..., divert_count: _Optional[int] = ...) -> None: ...

class InkDiagramChoice(_message.Message):
    __slots__ = ("text", "divert", "is_sticky_choice")
    TEXT_FIELD_NUMBER: _ClassVar[int]
    DIVERT_FIELD_NUMBER: _ClassVar[int]
    IS_STICKY_CHOICE_FIELD_NUMBER: _ClassVar[int]
    text: str
    divert: str
    is_sticky_choice: bool
    def __init__(self, text: _Optional[str] = ..., divert: _Optional[str] = ..., is_sticky_choice: bool = ...) -> None: ...
