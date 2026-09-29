// バックエンドが返すタイムゾーン無しのISO文字列(例: "2026-10-10T13:00:00")を
// "2026/10/10 13:00"のように読みやすく整形する。
// Dateオブジェクトを使うとタイムゾーン変換が入ってしまうため、文字列操作のみで行う。
export function formatDateTime(isoString: string): string {
  const [datePart, timePart] = isoString.split('T')
  const formattedDate = (datePart ?? '').replace(/-/g, '/')
  const formattedTime = (timePart ?? '').slice(0, 5)

  return `${formattedDate} ${formattedTime}`.trim()
}
