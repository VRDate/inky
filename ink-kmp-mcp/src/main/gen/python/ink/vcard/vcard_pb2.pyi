from google.protobuf.internal import containers as _containers
from google.protobuf.internal import enum_type_wrapper as _enum_type_wrapper
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from typing import ClassVar as _ClassVar, Iterable as _Iterable, Mapping as _Mapping, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class VCardVersion(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
    __slots__ = ()
    VCARD_VERSION_UNSPECIFIED: _ClassVar[VCardVersion]
    V3_0: _ClassVar[VCardVersion]
    V4_0: _ClassVar[VCardVersion]

class TelType(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
    __slots__ = ()
    TEL_TYPE_UNSPECIFIED: _ClassVar[TelType]
    VOICE: _ClassVar[TelType]
    TEXT: _ClassVar[TelType]
    FAX: _ClassVar[TelType]
    CELL: _ClassVar[TelType]
    VIDEO: _ClassVar[TelType]
    PAGER: _ClassVar[TelType]

class AdrType(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
    __slots__ = ()
    ADR_TYPE_UNSPECIFIED: _ClassVar[AdrType]
    HOME: _ClassVar[AdrType]
    WORK: _ClassVar[AdrType]

class EmailType(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
    __slots__ = ()
    EMAIL_TYPE_UNSPECIFIED: _ClassVar[EmailType]
    EMAIL_HOME: _ClassVar[EmailType]
    EMAIL_WORK: _ClassVar[EmailType]
VCARD_VERSION_UNSPECIFIED: VCardVersion
V3_0: VCardVersion
V4_0: VCardVersion
TEL_TYPE_UNSPECIFIED: TelType
VOICE: TelType
TEXT: TelType
FAX: TelType
CELL: TelType
VIDEO: TelType
PAGER: TelType
ADR_TYPE_UNSPECIFIED: AdrType
HOME: AdrType
WORK: AdrType
EMAIL_TYPE_UNSPECIFIED: EmailType
EMAIL_HOME: EmailType
EMAIL_WORK: EmailType

class VCardName(_message.Message):
    __slots__ = ("family", "given", "additional", "prefix", "suffix")
    FAMILY_FIELD_NUMBER: _ClassVar[int]
    GIVEN_FIELD_NUMBER: _ClassVar[int]
    ADDITIONAL_FIELD_NUMBER: _ClassVar[int]
    PREFIX_FIELD_NUMBER: _ClassVar[int]
    SUFFIX_FIELD_NUMBER: _ClassVar[int]
    family: str
    given: str
    additional: str
    prefix: str
    suffix: str
    def __init__(self, family: _Optional[str] = ..., given: _Optional[str] = ..., additional: _Optional[str] = ..., prefix: _Optional[str] = ..., suffix: _Optional[str] = ...) -> None: ...

class VCardAddress(_message.Message):
    __slots__ = ("type", "po_box", "extended", "street", "locality", "region", "postal_code", "country", "label")
    TYPE_FIELD_NUMBER: _ClassVar[int]
    PO_BOX_FIELD_NUMBER: _ClassVar[int]
    EXTENDED_FIELD_NUMBER: _ClassVar[int]
    STREET_FIELD_NUMBER: _ClassVar[int]
    LOCALITY_FIELD_NUMBER: _ClassVar[int]
    REGION_FIELD_NUMBER: _ClassVar[int]
    POSTAL_CODE_FIELD_NUMBER: _ClassVar[int]
    COUNTRY_FIELD_NUMBER: _ClassVar[int]
    LABEL_FIELD_NUMBER: _ClassVar[int]
    type: AdrType
    po_box: str
    extended: str
    street: str
    locality: str
    region: str
    postal_code: str
    country: str
    label: str
    def __init__(self, type: _Optional[_Union[AdrType, str]] = ..., po_box: _Optional[str] = ..., extended: _Optional[str] = ..., street: _Optional[str] = ..., locality: _Optional[str] = ..., region: _Optional[str] = ..., postal_code: _Optional[str] = ..., country: _Optional[str] = ..., label: _Optional[str] = ...) -> None: ...

class VCardTelephone(_message.Message):
    __slots__ = ("type", "value", "is_preferred")
    TYPE_FIELD_NUMBER: _ClassVar[int]
    VALUE_FIELD_NUMBER: _ClassVar[int]
    IS_PREFERRED_FIELD_NUMBER: _ClassVar[int]
    type: TelType
    value: str
    is_preferred: bool
    def __init__(self, type: _Optional[_Union[TelType, str]] = ..., value: _Optional[str] = ..., is_preferred: bool = ...) -> None: ...

class VCardEmail(_message.Message):
    __slots__ = ("type", "value", "is_preferred")
    TYPE_FIELD_NUMBER: _ClassVar[int]
    VALUE_FIELD_NUMBER: _ClassVar[int]
    IS_PREFERRED_FIELD_NUMBER: _ClassVar[int]
    type: EmailType
    value: str
    is_preferred: bool
    def __init__(self, type: _Optional[_Union[EmailType, str]] = ..., value: _Optional[str] = ..., is_preferred: bool = ...) -> None: ...

class VCardOrganization(_message.Message):
    __slots__ = ("name", "unit")
    NAME_FIELD_NUMBER: _ClassVar[int]
    UNIT_FIELD_NUMBER: _ClassVar[int]
    name: str
    unit: str
    def __init__(self, name: _Optional[str] = ..., unit: _Optional[str] = ...) -> None: ...

class VCardImage(_message.Message):
    __slots__ = ("uri", "media_type", "data")
    URI_FIELD_NUMBER: _ClassVar[int]
    MEDIA_TYPE_FIELD_NUMBER: _ClassVar[int]
    DATA_FIELD_NUMBER: _ClassVar[int]
    uri: str
    media_type: str
    data: bytes
    def __init__(self, uri: _Optional[str] = ..., media_type: _Optional[str] = ..., data: _Optional[bytes] = ...) -> None: ...

class VCardExtProperty(_message.Message):
    __slots__ = ("name", "value")
    NAME_FIELD_NUMBER: _ClassVar[int]
    VALUE_FIELD_NUMBER: _ClassVar[int]
    name: str
    value: str
    def __init__(self, name: _Optional[str] = ..., value: _Optional[str] = ...) -> None: ...

class InkVCard(_message.Message):
    __slots__ = ("uid", "version", "formatted_name", "name", "emails", "telephones", "addresses", "org", "title", "role", "photo", "logo", "urls", "categories", "note", "key", "rev", "is_llm", "roles", "folder_path", "mcp_uri", "jcard_json", "ext_properties", "language", "timezone")
    UID_FIELD_NUMBER: _ClassVar[int]
    VERSION_FIELD_NUMBER: _ClassVar[int]
    FORMATTED_NAME_FIELD_NUMBER: _ClassVar[int]
    NAME_FIELD_NUMBER: _ClassVar[int]
    EMAILS_FIELD_NUMBER: _ClassVar[int]
    TELEPHONES_FIELD_NUMBER: _ClassVar[int]
    ADDRESSES_FIELD_NUMBER: _ClassVar[int]
    ORG_FIELD_NUMBER: _ClassVar[int]
    TITLE_FIELD_NUMBER: _ClassVar[int]
    ROLE_FIELD_NUMBER: _ClassVar[int]
    PHOTO_FIELD_NUMBER: _ClassVar[int]
    LOGO_FIELD_NUMBER: _ClassVar[int]
    URLS_FIELD_NUMBER: _ClassVar[int]
    CATEGORIES_FIELD_NUMBER: _ClassVar[int]
    NOTE_FIELD_NUMBER: _ClassVar[int]
    KEY_FIELD_NUMBER: _ClassVar[int]
    REV_FIELD_NUMBER: _ClassVar[int]
    IS_LLM_FIELD_NUMBER: _ClassVar[int]
    ROLES_FIELD_NUMBER: _ClassVar[int]
    FOLDER_PATH_FIELD_NUMBER: _ClassVar[int]
    MCP_URI_FIELD_NUMBER: _ClassVar[int]
    JCARD_JSON_FIELD_NUMBER: _ClassVar[int]
    EXT_PROPERTIES_FIELD_NUMBER: _ClassVar[int]
    LANGUAGE_FIELD_NUMBER: _ClassVar[int]
    TIMEZONE_FIELD_NUMBER: _ClassVar[int]
    uid: str
    version: VCardVersion
    formatted_name: str
    name: VCardName
    emails: _containers.RepeatedCompositeFieldContainer[VCardEmail]
    telephones: _containers.RepeatedCompositeFieldContainer[VCardTelephone]
    addresses: _containers.RepeatedCompositeFieldContainer[VCardAddress]
    org: VCardOrganization
    title: str
    role: str
    photo: VCardImage
    logo: VCardImage
    urls: _containers.RepeatedScalarFieldContainer[str]
    categories: _containers.RepeatedScalarFieldContainer[str]
    note: str
    key: str
    rev: str
    is_llm: bool
    roles: _containers.RepeatedScalarFieldContainer[str]
    folder_path: str
    mcp_uri: str
    jcard_json: str
    ext_properties: _containers.RepeatedCompositeFieldContainer[VCardExtProperty]
    language: str
    timezone: str
    def __init__(self, uid: _Optional[str] = ..., version: _Optional[_Union[VCardVersion, str]] = ..., formatted_name: _Optional[str] = ..., name: _Optional[_Union[VCardName, _Mapping]] = ..., emails: _Optional[_Iterable[_Union[VCardEmail, _Mapping]]] = ..., telephones: _Optional[_Iterable[_Union[VCardTelephone, _Mapping]]] = ..., addresses: _Optional[_Iterable[_Union[VCardAddress, _Mapping]]] = ..., org: _Optional[_Union[VCardOrganization, _Mapping]] = ..., title: _Optional[str] = ..., role: _Optional[str] = ..., photo: _Optional[_Union[VCardImage, _Mapping]] = ..., logo: _Optional[_Union[VCardImage, _Mapping]] = ..., urls: _Optional[_Iterable[str]] = ..., categories: _Optional[_Iterable[str]] = ..., note: _Optional[str] = ..., key: _Optional[str] = ..., rev: _Optional[str] = ..., is_llm: bool = ..., roles: _Optional[_Iterable[str]] = ..., folder_path: _Optional[str] = ..., mcp_uri: _Optional[str] = ..., jcard_json: _Optional[str] = ..., ext_properties: _Optional[_Iterable[_Union[VCardExtProperty, _Mapping]]] = ..., language: _Optional[str] = ..., timezone: _Optional[str] = ...) -> None: ...

class JCard(_message.Message):
    __slots__ = ("json", "parsed")
    JSON_FIELD_NUMBER: _ClassVar[int]
    PARSED_FIELD_NUMBER: _ClassVar[int]
    json: str
    parsed: InkVCard
    def __init__(self, json: _Optional[str] = ..., parsed: _Optional[_Union[InkVCard, _Mapping]] = ...) -> None: ...
