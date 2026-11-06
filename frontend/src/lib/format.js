// AI-assisted formatting with Codex. SQL Timestamp JSON has a zone; order LocalDateTime JSON does not.
export function formatTime(value) {
  if (!value) return "—";
  const text = String(value);
  if (!/([zZ]|[+-]\d{2}:\d{2})$/.test(text))
    return text.replace("T", " ").slice(0, 16);
  const date = new Date(text);
  if (Number.isNaN(date.getTime())) return "—";
  const parts = new Intl.DateTimeFormat("zh-CN", {
    timeZone: "Asia/Shanghai",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  }).formatToParts(date);
  const values = Object.fromEntries(
    parts.map((part) => [part.type, part.value]),
  );
  return `${values.year}-${values.month}-${values.day} ${values.hour}:${values.minute}`;
}
