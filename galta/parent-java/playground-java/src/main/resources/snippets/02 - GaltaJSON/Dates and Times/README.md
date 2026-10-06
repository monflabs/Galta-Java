# Dates and Times

JSON has no date type: the `java.time` values are written as ISO-8601 strings, and the
typed getters (`getLocalDate`, `getZonedDateTime`...) parse them back.
