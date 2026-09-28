// Each alternative can only ever match one at a time, so both can safely
// reuse the same group names - previously a SyntaxError.
const date = /^(?<year>\d{4})-(?<month>\d{2})-(?<day>\d{2})$|^(?<month>\d{2})\/(?<day>\d{2})\/(?<year>\d{4})$/;

const iso = date.exec("2026-03-05");
console.log(iso.groups.year, iso.groups.month, iso.groups.day);

const us = date.exec("03/05/2026");
console.log(us.groups.year, us.groups.month, us.groups.day);
