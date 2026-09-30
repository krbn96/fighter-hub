// Tournament.teamSize(N)を"N ON N"の対戦形式表記に整形する(例: 3 -> "3 ON 3")。
export function formatTeamSize(teamSize: number): string {
  return `${teamSize} ON ${teamSize}`
}
