from google.protobuf.internal import containers as _containers
from google.protobuf.internal import enum_type_wrapper as _enum_type_wrapper
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from typing import ClassVar as _ClassVar, Iterable as _Iterable, Mapping as _Mapping, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class EventStatus(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
    __slots__ = ()
    EVENT_STATUS_UNSPECIFIED: _ClassVar[EventStatus]
    TENTATIVE: _ClassVar[EventStatus]
    CONFIRMED: _ClassVar[EventStatus]
    CANCELLED: _ClassVar[EventStatus]

class TodoStatus(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
    __slots__ = ()
    TODO_STATUS_UNSPECIFIED: _ClassVar[TodoStatus]
    NEEDS_ACTION: _ClassVar[TodoStatus]
    COMPLETED: _ClassVar[TodoStatus]
    IN_PROCESS: _ClassVar[TodoStatus]
    TODO_CANCELLED: _ClassVar[TodoStatus]

class ParticipationStatus(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
    __slots__ = ()
    PARTSTAT_UNSPECIFIED: _ClassVar[ParticipationStatus]
    ACCEPTED: _ClassVar[ParticipationStatus]
    DECLINED: _ClassVar[ParticipationStatus]
    TENTATIVE_PARTSTAT: _ClassVar[ParticipationStatus]
    DELEGATED: _ClassVar[ParticipationStatus]
    NEEDS_ACTION_PARTSTAT: _ClassVar[ParticipationStatus]

class ParticipantRole(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
    __slots__ = ()
    ROLE_UNSPECIFIED: _ClassVar[ParticipantRole]
    CHAIR: _ClassVar[ParticipantRole]
    REQ_PARTICIPANT: _ClassVar[ParticipantRole]
    OPT_PARTICIPANT: _ClassVar[ParticipantRole]
    NON_PARTICIPANT: _ClassVar[ParticipantRole]

class RecurrenceFrequency(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
    __slots__ = ()
    FREQ_UNSPECIFIED: _ClassVar[RecurrenceFrequency]
    SECONDLY: _ClassVar[RecurrenceFrequency]
    MINUTELY: _ClassVar[RecurrenceFrequency]
    HOURLY: _ClassVar[RecurrenceFrequency]
    DAILY: _ClassVar[RecurrenceFrequency]
    WEEKLY: _ClassVar[RecurrenceFrequency]
    MONTHLY: _ClassVar[RecurrenceFrequency]
    YEARLY: _ClassVar[RecurrenceFrequency]

class Weekday(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
    __slots__ = ()
    WEEKDAY_UNSPECIFIED: _ClassVar[Weekday]
    MO: _ClassVar[Weekday]
    TU: _ClassVar[Weekday]
    WE: _ClassVar[Weekday]
    TH: _ClassVar[Weekday]
    FR: _ClassVar[Weekday]
    SA: _ClassVar[Weekday]
    SU: _ClassVar[Weekday]

class AlarmAction(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
    __slots__ = ()
    ALARM_ACTION_UNSPECIFIED: _ClassVar[AlarmAction]
    DISPLAY: _ClassVar[AlarmAction]
    AUDIO: _ClassVar[AlarmAction]
    EMAIL: _ClassVar[AlarmAction]

class Transparency(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
    __slots__ = ()
    TRANSP_UNSPECIFIED: _ClassVar[Transparency]
    OPAQUE: _ClassVar[Transparency]
    TRANSPARENT: _ClassVar[Transparency]

class AccessClassification(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
    __slots__ = ()
    CLASS_UNSPECIFIED: _ClassVar[AccessClassification]
    PUBLIC: _ClassVar[AccessClassification]
    PRIVATE: _ClassVar[AccessClassification]
    CONFIDENTIAL: _ClassVar[AccessClassification]
EVENT_STATUS_UNSPECIFIED: EventStatus
TENTATIVE: EventStatus
CONFIRMED: EventStatus
CANCELLED: EventStatus
TODO_STATUS_UNSPECIFIED: TodoStatus
NEEDS_ACTION: TodoStatus
COMPLETED: TodoStatus
IN_PROCESS: TodoStatus
TODO_CANCELLED: TodoStatus
PARTSTAT_UNSPECIFIED: ParticipationStatus
ACCEPTED: ParticipationStatus
DECLINED: ParticipationStatus
TENTATIVE_PARTSTAT: ParticipationStatus
DELEGATED: ParticipationStatus
NEEDS_ACTION_PARTSTAT: ParticipationStatus
ROLE_UNSPECIFIED: ParticipantRole
CHAIR: ParticipantRole
REQ_PARTICIPANT: ParticipantRole
OPT_PARTICIPANT: ParticipantRole
NON_PARTICIPANT: ParticipantRole
FREQ_UNSPECIFIED: RecurrenceFrequency
SECONDLY: RecurrenceFrequency
MINUTELY: RecurrenceFrequency
HOURLY: RecurrenceFrequency
DAILY: RecurrenceFrequency
WEEKLY: RecurrenceFrequency
MONTHLY: RecurrenceFrequency
YEARLY: RecurrenceFrequency
WEEKDAY_UNSPECIFIED: Weekday
MO: Weekday
TU: Weekday
WE: Weekday
TH: Weekday
FR: Weekday
SA: Weekday
SU: Weekday
ALARM_ACTION_UNSPECIFIED: AlarmAction
DISPLAY: AlarmAction
AUDIO: AlarmAction
EMAIL: AlarmAction
TRANSP_UNSPECIFIED: Transparency
OPAQUE: Transparency
TRANSPARENT: Transparency
CLASS_UNSPECIFIED: AccessClassification
PUBLIC: AccessClassification
PRIVATE: AccessClassification
CONFIDENTIAL: AccessClassification

class WeekdayNum(_message.Message):
    __slots__ = ("ordinal", "day")
    ORDINAL_FIELD_NUMBER: _ClassVar[int]
    DAY_FIELD_NUMBER: _ClassVar[int]
    ordinal: int
    day: Weekday
    def __init__(self, ordinal: _Optional[int] = ..., day: _Optional[_Union[Weekday, str]] = ...) -> None: ...

class RecurrenceRule(_message.Message):
    __slots__ = ("freq", "interval", "count", "until", "by_day", "by_month_day", "by_year_day", "by_week_no", "by_month", "by_set_pos", "week_start", "by_hour", "by_minute", "by_second")
    FREQ_FIELD_NUMBER: _ClassVar[int]
    INTERVAL_FIELD_NUMBER: _ClassVar[int]
    COUNT_FIELD_NUMBER: _ClassVar[int]
    UNTIL_FIELD_NUMBER: _ClassVar[int]
    BY_DAY_FIELD_NUMBER: _ClassVar[int]
    BY_MONTH_DAY_FIELD_NUMBER: _ClassVar[int]
    BY_YEAR_DAY_FIELD_NUMBER: _ClassVar[int]
    BY_WEEK_NO_FIELD_NUMBER: _ClassVar[int]
    BY_MONTH_FIELD_NUMBER: _ClassVar[int]
    BY_SET_POS_FIELD_NUMBER: _ClassVar[int]
    WEEK_START_FIELD_NUMBER: _ClassVar[int]
    BY_HOUR_FIELD_NUMBER: _ClassVar[int]
    BY_MINUTE_FIELD_NUMBER: _ClassVar[int]
    BY_SECOND_FIELD_NUMBER: _ClassVar[int]
    freq: RecurrenceFrequency
    interval: int
    count: int
    until: str
    by_day: _containers.RepeatedCompositeFieldContainer[WeekdayNum]
    by_month_day: _containers.RepeatedScalarFieldContainer[int]
    by_year_day: _containers.RepeatedScalarFieldContainer[int]
    by_week_no: _containers.RepeatedScalarFieldContainer[int]
    by_month: _containers.RepeatedScalarFieldContainer[int]
    by_set_pos: _containers.RepeatedScalarFieldContainer[int]
    week_start: Weekday
    by_hour: _containers.RepeatedScalarFieldContainer[int]
    by_minute: _containers.RepeatedScalarFieldContainer[int]
    by_second: _containers.RepeatedScalarFieldContainer[int]
    def __init__(self, freq: _Optional[_Union[RecurrenceFrequency, str]] = ..., interval: _Optional[int] = ..., count: _Optional[int] = ..., until: _Optional[str] = ..., by_day: _Optional[_Iterable[_Union[WeekdayNum, _Mapping]]] = ..., by_month_day: _Optional[_Iterable[int]] = ..., by_year_day: _Optional[_Iterable[int]] = ..., by_week_no: _Optional[_Iterable[int]] = ..., by_month: _Optional[_Iterable[int]] = ..., by_set_pos: _Optional[_Iterable[int]] = ..., week_start: _Optional[_Union[Weekday, str]] = ..., by_hour: _Optional[_Iterable[int]] = ..., by_minute: _Optional[_Iterable[int]] = ..., by_second: _Optional[_Iterable[int]] = ...) -> None: ...

class Attendee(_message.Message):
    __slots__ = ("cal_address", "common_name", "role", "partstat", "rsvp", "delegated_from", "delegated_to", "sent_by")
    CAL_ADDRESS_FIELD_NUMBER: _ClassVar[int]
    COMMON_NAME_FIELD_NUMBER: _ClassVar[int]
    ROLE_FIELD_NUMBER: _ClassVar[int]
    PARTSTAT_FIELD_NUMBER: _ClassVar[int]
    RSVP_FIELD_NUMBER: _ClassVar[int]
    DELEGATED_FROM_FIELD_NUMBER: _ClassVar[int]
    DELEGATED_TO_FIELD_NUMBER: _ClassVar[int]
    SENT_BY_FIELD_NUMBER: _ClassVar[int]
    cal_address: str
    common_name: str
    role: ParticipantRole
    partstat: ParticipationStatus
    rsvp: bool
    delegated_from: str
    delegated_to: str
    sent_by: str
    def __init__(self, cal_address: _Optional[str] = ..., common_name: _Optional[str] = ..., role: _Optional[_Union[ParticipantRole, str]] = ..., partstat: _Optional[_Union[ParticipationStatus, str]] = ..., rsvp: bool = ..., delegated_from: _Optional[str] = ..., delegated_to: _Optional[str] = ..., sent_by: _Optional[str] = ...) -> None: ...

class Organizer(_message.Message):
    __slots__ = ("cal_address", "common_name", "sent_by")
    CAL_ADDRESS_FIELD_NUMBER: _ClassVar[int]
    COMMON_NAME_FIELD_NUMBER: _ClassVar[int]
    SENT_BY_FIELD_NUMBER: _ClassVar[int]
    cal_address: str
    common_name: str
    sent_by: str
    def __init__(self, cal_address: _Optional[str] = ..., common_name: _Optional[str] = ..., sent_by: _Optional[str] = ...) -> None: ...

class Alarm(_message.Message):
    __slots__ = ("action", "trigger", "trigger_absolute", "description", "summary", "repeat_count", "repeat_duration")
    ACTION_FIELD_NUMBER: _ClassVar[int]
    TRIGGER_FIELD_NUMBER: _ClassVar[int]
    TRIGGER_ABSOLUTE_FIELD_NUMBER: _ClassVar[int]
    DESCRIPTION_FIELD_NUMBER: _ClassVar[int]
    SUMMARY_FIELD_NUMBER: _ClassVar[int]
    REPEAT_COUNT_FIELD_NUMBER: _ClassVar[int]
    REPEAT_DURATION_FIELD_NUMBER: _ClassVar[int]
    action: AlarmAction
    trigger: str
    trigger_absolute: bool
    description: str
    summary: str
    repeat_count: int
    repeat_duration: str
    def __init__(self, action: _Optional[_Union[AlarmAction, str]] = ..., trigger: _Optional[str] = ..., trigger_absolute: bool = ..., description: _Optional[str] = ..., summary: _Optional[str] = ..., repeat_count: _Optional[int] = ..., repeat_duration: _Optional[str] = ...) -> None: ...

class ICalDuration(_message.Message):
    __slots__ = ("negative", "weeks", "days", "hours", "minutes", "seconds")
    NEGATIVE_FIELD_NUMBER: _ClassVar[int]
    WEEKS_FIELD_NUMBER: _ClassVar[int]
    DAYS_FIELD_NUMBER: _ClassVar[int]
    HOURS_FIELD_NUMBER: _ClassVar[int]
    MINUTES_FIELD_NUMBER: _ClassVar[int]
    SECONDS_FIELD_NUMBER: _ClassVar[int]
    negative: bool
    weeks: int
    days: int
    hours: int
    minutes: int
    seconds: int
    def __init__(self, negative: bool = ..., weeks: _Optional[int] = ..., days: _Optional[int] = ..., hours: _Optional[int] = ..., minutes: _Optional[int] = ..., seconds: _Optional[int] = ...) -> None: ...

class GeoPosition(_message.Message):
    __slots__ = ("latitude", "longitude")
    LATITUDE_FIELD_NUMBER: _ClassVar[int]
    LONGITUDE_FIELD_NUMBER: _ClassVar[int]
    latitude: float
    longitude: float
    def __init__(self, latitude: _Optional[float] = ..., longitude: _Optional[float] = ...) -> None: ...

class VEvent(_message.Message):
    __slots__ = ("uid", "sequence", "dt_start", "dt_end", "duration", "dt_stamp", "summary", "description", "location", "geo", "url", "status", "classification", "transp", "priority", "categories", "resources", "organizer", "attendees", "rrule", "rdate", "exdate", "recurrence_id", "alarms", "created", "last_modified", "related_to", "attachments", "color", "conferences")
    UID_FIELD_NUMBER: _ClassVar[int]
    SEQUENCE_FIELD_NUMBER: _ClassVar[int]
    DT_START_FIELD_NUMBER: _ClassVar[int]
    DT_END_FIELD_NUMBER: _ClassVar[int]
    DURATION_FIELD_NUMBER: _ClassVar[int]
    DT_STAMP_FIELD_NUMBER: _ClassVar[int]
    SUMMARY_FIELD_NUMBER: _ClassVar[int]
    DESCRIPTION_FIELD_NUMBER: _ClassVar[int]
    LOCATION_FIELD_NUMBER: _ClassVar[int]
    GEO_FIELD_NUMBER: _ClassVar[int]
    URL_FIELD_NUMBER: _ClassVar[int]
    STATUS_FIELD_NUMBER: _ClassVar[int]
    CLASSIFICATION_FIELD_NUMBER: _ClassVar[int]
    TRANSP_FIELD_NUMBER: _ClassVar[int]
    PRIORITY_FIELD_NUMBER: _ClassVar[int]
    CATEGORIES_FIELD_NUMBER: _ClassVar[int]
    RESOURCES_FIELD_NUMBER: _ClassVar[int]
    ORGANIZER_FIELD_NUMBER: _ClassVar[int]
    ATTENDEES_FIELD_NUMBER: _ClassVar[int]
    RRULE_FIELD_NUMBER: _ClassVar[int]
    RDATE_FIELD_NUMBER: _ClassVar[int]
    EXDATE_FIELD_NUMBER: _ClassVar[int]
    RECURRENCE_ID_FIELD_NUMBER: _ClassVar[int]
    ALARMS_FIELD_NUMBER: _ClassVar[int]
    CREATED_FIELD_NUMBER: _ClassVar[int]
    LAST_MODIFIED_FIELD_NUMBER: _ClassVar[int]
    RELATED_TO_FIELD_NUMBER: _ClassVar[int]
    ATTACHMENTS_FIELD_NUMBER: _ClassVar[int]
    COLOR_FIELD_NUMBER: _ClassVar[int]
    CONFERENCES_FIELD_NUMBER: _ClassVar[int]
    uid: str
    sequence: int
    dt_start: str
    dt_end: str
    duration: ICalDuration
    dt_stamp: str
    summary: str
    description: str
    location: str
    geo: GeoPosition
    url: str
    status: EventStatus
    classification: AccessClassification
    transp: Transparency
    priority: int
    categories: _containers.RepeatedScalarFieldContainer[str]
    resources: _containers.RepeatedScalarFieldContainer[str]
    organizer: Organizer
    attendees: _containers.RepeatedCompositeFieldContainer[Attendee]
    rrule: RecurrenceRule
    rdate: _containers.RepeatedScalarFieldContainer[str]
    exdate: _containers.RepeatedScalarFieldContainer[str]
    recurrence_id: str
    alarms: _containers.RepeatedCompositeFieldContainer[Alarm]
    created: str
    last_modified: str
    related_to: str
    attachments: _containers.RepeatedScalarFieldContainer[str]
    color: str
    conferences: _containers.RepeatedScalarFieldContainer[str]
    def __init__(self, uid: _Optional[str] = ..., sequence: _Optional[int] = ..., dt_start: _Optional[str] = ..., dt_end: _Optional[str] = ..., duration: _Optional[_Union[ICalDuration, _Mapping]] = ..., dt_stamp: _Optional[str] = ..., summary: _Optional[str] = ..., description: _Optional[str] = ..., location: _Optional[str] = ..., geo: _Optional[_Union[GeoPosition, _Mapping]] = ..., url: _Optional[str] = ..., status: _Optional[_Union[EventStatus, str]] = ..., classification: _Optional[_Union[AccessClassification, str]] = ..., transp: _Optional[_Union[Transparency, str]] = ..., priority: _Optional[int] = ..., categories: _Optional[_Iterable[str]] = ..., resources: _Optional[_Iterable[str]] = ..., organizer: _Optional[_Union[Organizer, _Mapping]] = ..., attendees: _Optional[_Iterable[_Union[Attendee, _Mapping]]] = ..., rrule: _Optional[_Union[RecurrenceRule, _Mapping]] = ..., rdate: _Optional[_Iterable[str]] = ..., exdate: _Optional[_Iterable[str]] = ..., recurrence_id: _Optional[str] = ..., alarms: _Optional[_Iterable[_Union[Alarm, _Mapping]]] = ..., created: _Optional[str] = ..., last_modified: _Optional[str] = ..., related_to: _Optional[str] = ..., attachments: _Optional[_Iterable[str]] = ..., color: _Optional[str] = ..., conferences: _Optional[_Iterable[str]] = ...) -> None: ...

class VTodo(_message.Message):
    __slots__ = ("uid", "sequence", "dt_start", "due", "duration", "completed", "summary", "description", "location", "status", "classification", "priority", "percent_complete", "categories", "organizer", "attendees", "rrule", "alarms", "created", "last_modified", "related_to")
    UID_FIELD_NUMBER: _ClassVar[int]
    SEQUENCE_FIELD_NUMBER: _ClassVar[int]
    DT_START_FIELD_NUMBER: _ClassVar[int]
    DUE_FIELD_NUMBER: _ClassVar[int]
    DURATION_FIELD_NUMBER: _ClassVar[int]
    COMPLETED_FIELD_NUMBER: _ClassVar[int]
    SUMMARY_FIELD_NUMBER: _ClassVar[int]
    DESCRIPTION_FIELD_NUMBER: _ClassVar[int]
    LOCATION_FIELD_NUMBER: _ClassVar[int]
    STATUS_FIELD_NUMBER: _ClassVar[int]
    CLASSIFICATION_FIELD_NUMBER: _ClassVar[int]
    PRIORITY_FIELD_NUMBER: _ClassVar[int]
    PERCENT_COMPLETE_FIELD_NUMBER: _ClassVar[int]
    CATEGORIES_FIELD_NUMBER: _ClassVar[int]
    ORGANIZER_FIELD_NUMBER: _ClassVar[int]
    ATTENDEES_FIELD_NUMBER: _ClassVar[int]
    RRULE_FIELD_NUMBER: _ClassVar[int]
    ALARMS_FIELD_NUMBER: _ClassVar[int]
    CREATED_FIELD_NUMBER: _ClassVar[int]
    LAST_MODIFIED_FIELD_NUMBER: _ClassVar[int]
    RELATED_TO_FIELD_NUMBER: _ClassVar[int]
    uid: str
    sequence: int
    dt_start: str
    due: str
    duration: ICalDuration
    completed: str
    summary: str
    description: str
    location: str
    status: TodoStatus
    classification: AccessClassification
    priority: int
    percent_complete: int
    categories: _containers.RepeatedScalarFieldContainer[str]
    organizer: Organizer
    attendees: _containers.RepeatedCompositeFieldContainer[Attendee]
    rrule: RecurrenceRule
    alarms: _containers.RepeatedCompositeFieldContainer[Alarm]
    created: str
    last_modified: str
    related_to: str
    def __init__(self, uid: _Optional[str] = ..., sequence: _Optional[int] = ..., dt_start: _Optional[str] = ..., due: _Optional[str] = ..., duration: _Optional[_Union[ICalDuration, _Mapping]] = ..., completed: _Optional[str] = ..., summary: _Optional[str] = ..., description: _Optional[str] = ..., location: _Optional[str] = ..., status: _Optional[_Union[TodoStatus, str]] = ..., classification: _Optional[_Union[AccessClassification, str]] = ..., priority: _Optional[int] = ..., percent_complete: _Optional[int] = ..., categories: _Optional[_Iterable[str]] = ..., organizer: _Optional[_Union[Organizer, _Mapping]] = ..., attendees: _Optional[_Iterable[_Union[Attendee, _Mapping]]] = ..., rrule: _Optional[_Union[RecurrenceRule, _Mapping]] = ..., alarms: _Optional[_Iterable[_Union[Alarm, _Mapping]]] = ..., created: _Optional[str] = ..., last_modified: _Optional[str] = ..., related_to: _Optional[str] = ...) -> None: ...

class VJournal(_message.Message):
    __slots__ = ("uid", "sequence", "dt_start", "summary", "description", "status", "classification", "categories", "organizer", "attendees", "rrule", "created", "last_modified", "related_to")
    UID_FIELD_NUMBER: _ClassVar[int]
    SEQUENCE_FIELD_NUMBER: _ClassVar[int]
    DT_START_FIELD_NUMBER: _ClassVar[int]
    SUMMARY_FIELD_NUMBER: _ClassVar[int]
    DESCRIPTION_FIELD_NUMBER: _ClassVar[int]
    STATUS_FIELD_NUMBER: _ClassVar[int]
    CLASSIFICATION_FIELD_NUMBER: _ClassVar[int]
    CATEGORIES_FIELD_NUMBER: _ClassVar[int]
    ORGANIZER_FIELD_NUMBER: _ClassVar[int]
    ATTENDEES_FIELD_NUMBER: _ClassVar[int]
    RRULE_FIELD_NUMBER: _ClassVar[int]
    CREATED_FIELD_NUMBER: _ClassVar[int]
    LAST_MODIFIED_FIELD_NUMBER: _ClassVar[int]
    RELATED_TO_FIELD_NUMBER: _ClassVar[int]
    uid: str
    sequence: int
    dt_start: str
    summary: str
    description: str
    status: EventStatus
    classification: AccessClassification
    categories: _containers.RepeatedScalarFieldContainer[str]
    organizer: Organizer
    attendees: _containers.RepeatedCompositeFieldContainer[Attendee]
    rrule: RecurrenceRule
    created: str
    last_modified: str
    related_to: str
    def __init__(self, uid: _Optional[str] = ..., sequence: _Optional[int] = ..., dt_start: _Optional[str] = ..., summary: _Optional[str] = ..., description: _Optional[str] = ..., status: _Optional[_Union[EventStatus, str]] = ..., classification: _Optional[_Union[AccessClassification, str]] = ..., categories: _Optional[_Iterable[str]] = ..., organizer: _Optional[_Union[Organizer, _Mapping]] = ..., attendees: _Optional[_Iterable[_Union[Attendee, _Mapping]]] = ..., rrule: _Optional[_Union[RecurrenceRule, _Mapping]] = ..., created: _Optional[str] = ..., last_modified: _Optional[str] = ..., related_to: _Optional[str] = ...) -> None: ...

class VCalendar(_message.Message):
    __slots__ = ("prod_id", "version", "cal_scale", "method", "name", "description", "color", "source", "refresh_interval", "events", "todos", "journals")
    PROD_ID_FIELD_NUMBER: _ClassVar[int]
    VERSION_FIELD_NUMBER: _ClassVar[int]
    CAL_SCALE_FIELD_NUMBER: _ClassVar[int]
    METHOD_FIELD_NUMBER: _ClassVar[int]
    NAME_FIELD_NUMBER: _ClassVar[int]
    DESCRIPTION_FIELD_NUMBER: _ClassVar[int]
    COLOR_FIELD_NUMBER: _ClassVar[int]
    SOURCE_FIELD_NUMBER: _ClassVar[int]
    REFRESH_INTERVAL_FIELD_NUMBER: _ClassVar[int]
    EVENTS_FIELD_NUMBER: _ClassVar[int]
    TODOS_FIELD_NUMBER: _ClassVar[int]
    JOURNALS_FIELD_NUMBER: _ClassVar[int]
    prod_id: str
    version: str
    cal_scale: str
    method: str
    name: str
    description: str
    color: str
    source: str
    refresh_interval: str
    events: _containers.RepeatedCompositeFieldContainer[VEvent]
    todos: _containers.RepeatedCompositeFieldContainer[VTodo]
    journals: _containers.RepeatedCompositeFieldContainer[VJournal]
    def __init__(self, prod_id: _Optional[str] = ..., version: _Optional[str] = ..., cal_scale: _Optional[str] = ..., method: _Optional[str] = ..., name: _Optional[str] = ..., description: _Optional[str] = ..., color: _Optional[str] = ..., source: _Optional[str] = ..., refresh_interval: _Optional[str] = ..., events: _Optional[_Iterable[_Union[VEvent, _Mapping]]] = ..., todos: _Optional[_Iterable[_Union[VTodo, _Mapping]]] = ..., journals: _Optional[_Iterable[_Union[VJournal, _Mapping]]] = ...) -> None: ...
