package vn.htq.core.ui

// Derived from the locked 5.3 design specification; no media or answer data.
data class ScreenSpec(val id: String, val key: String, val taskCount: Int)

object ApprovedUi {
    const val contentVersion = "5.3-official-1"
    const val fontFamily = "Noto Sans"
    const val childMinDp = 56
    const val preferredDp = 64
    const val adultMinDp = 48
    const val marginDp = 16
    const val compactMarginDp = 12
    const val gapDp = 12
    const val titleSp = 28
    const val instructionSp = 24
    const val buttonSp = 22
    const val secondarySp = 18
    val palette: Map<String, String> = linkedMapOf(
        "cream" to "#FFF8EA",
        "ink" to "#243F43",
        "white" to "#FFFFFF",
        "green" to "#DDF0D8",
        "blue" to "#D9EBFA",
        "peach" to "#FBE2D9",
        "lilac" to "#F4E6FF",
        "yellow" to "#FFE9AB",
        "teal" to "#24664F",
        "teal_dark" to "#184C3C",
        "edge" to "#90B4A1",
        "muted" to "#556B6E",
        "shadow" to "#DCE4D6",
        "pink" to "#A83D63",
        "navy" to "#235A83",
        "softedge" to "#D3DCD2",
        "disabled" to "#E6E8E3",
        "disabledink" to "#4B6061",
        "focus" to "#235A83",
    )
    val screens: List<ScreenSpec> = listOf(
        ScreenSpec("SCR-001", "splash", 0),
        ScreenSpec("SCR-002", "welcome", 0),
        ScreenSpec("SCR-003", "home", 0),
        ScreenSpec("SCR-004", "area", 0),
        ScreenSpec("SCR-005", "topic", 0),
        ScreenSpec("SCR-006", "lesson", 0),
        ScreenSpec("SCR-007", "levels", 0),
        ScreenSpec("SCR-008", "explore_cards", 0),
        ScreenSpec("SCR-009", "letter_library", 0),
        ScreenSpec("SCR-010", "letter_detail", 0),
        ScreenSpec("SCR-011", "number_library", 0),
        ScreenSpec("SCR-012", "english_home", 0),
        ScreenSpec("SCR-013", "english_card", 0),
        ScreenSpec("SCR-014", "english_phrase", 0),
        ScreenSpec("SCR-015", "story_library", 0),
        ScreenSpec("SCR-016", "story_reader", 0),
        ScreenSpec("SCR-017", "song_reader", 0),
        ScreenSpec("SCR-018", "play_library", 0),
        ScreenSpec("SCR-019", "pause_panel", 0),
        ScreenSpec("SCR-020", "resume_panel", 0),
        ScreenSpec("SCR-021", "activity_end", 0),
        ScreenSpec("SCR-022", "reward_open", 0),
        ScreenSpec("SCR-023", "garden", 0),
        ScreenSpec("SCR-024", "inventory", 0),
        ScreenSpec("SCR-025", "parent_gate", 0),
        ScreenSpec("SCR-026", "parent_home", 0),
        ScreenSpec("SCR-027", "parent_progress", 0),
        ScreenSpec("SCR-028", "parent_audio", 0),
        ScreenSpec("SCR-029", "parent_time", 0),
        ScreenSpec("SCR-030", "parent_access", 0),
        ScreenSpec("SCR-031", "parent_reset", 0),
        ScreenSpec("SCR-032", "parent_about", 0),
        ScreenSpec("SCR-033", "error_panel", 0),
        ScreenSpec("SCR-034", "select_cards", 191),
        ScreenSpec("SCR-035", "select_compare", 10),
        ScreenSpec("SCR-036", "select_audio", 19),
        ScreenSpec("SCR-037", "select_scene", 59),
        ScreenSpec("SCR-038", "select_letter", 12),
        ScreenSpec("SCR-039", "select_clock", 2),
        ScreenSpec("SCR-040", "multi_cards", 17),
        ScreenSpec("SCR-041", "match_pairs", 70),
        ScreenSpec("SCR-042", "match_sound", 7),
        ScreenSpec("SCR-043", "match_shape", 8),
        ScreenSpec("SCR-044", "match_letters", 10),
        ScreenSpec("SCR-045", "match_position", 2),
        ScreenSpec("SCR-046", "match_clock", 4),
        ScreenSpec("SCR-047", "match_action", 3),
        ScreenSpec("SCR-048", "sort_bins", 31),
        ScreenSpec("SCR-049", "sequence_pattern", 10),
        ScreenSpec("SCR-050", "sequence_order", 30),
        ScreenSpec("SCR-051", "sequence_position", 3),
        ScreenSpec("SCR-052", "sequence_rhythm", 4),
        ScreenSpec("SCR-053", "sequence_instruction", 3),
        ScreenSpec("SCR-054", "count_pick", 8),
        ScreenSpec("SCR-055", "count_transfer", 12),
        ScreenSpec("SCR-056", "count_split", 6),
        ScreenSpec("SCR-057", "count_measure", 4),
        ScreenSpec("SCR-058", "count_listen", 2),
        ScreenSpec("SCR-059", "hotspot_word", 3),
        ScreenSpec("SCR-060", "hotspot_scene", 10),
        ScreenSpec("SCR-061", "memory_pairs", 6),
        ScreenSpec("SCR-062", "trace_path", 4),
        ScreenSpec("SCR-063", "free_draw", 10),
        ScreenSpec("SCR-064", "free_build", 9),
        ScreenSpec("SCR-065", "free_pick", 19),
        ScreenSpec("SCR-066", "free_story", 22),
        ScreenSpec("SCR-067", "free_rhythm", 7),
        ScreenSpec("SCR-068", "explore_sim", 6),
        ScreenSpec("SCR-069", "offline_invite", 37),
    )
    private val byId = screens.associateBy { it.id }
    fun screen(id: String): ScreenSpec = requireNotNull(byId[id]) { "Unknown screen" }
}
