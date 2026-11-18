import markers


def test_no_marker():
    parsed = markers.parse("Pick a topic, genius.")
    assert parsed.verdict == markers.NONE
    assert parsed.text == "Pick a topic, genius."


def test_correct_marker_is_stripped():
    parsed = markers.parse("Fine, that's right. [[CORRECT]]")
    assert parsed.verdict == markers.CORRECT
    assert parsed.text == "Fine, that's right."


def test_lenient_brackets_and_case():
    assert markers.parse("nope [ wrong ]]").verdict == markers.WRONG
    assert markers.parse("[[[Correct]]] yes").verdict == markers.CORRECT


def test_last_marker_wins():
    assert markers.parse("[[CORRECT]] wait, no. [[WRONG]]").verdict == markers.WRONG


def test_marker_only_reply_gets_fallback_text():
    assert markers.parse("[[CORRECT]]").text == markers.EMPTY_REPLY_FALLBACK


def test_blank_lines_collapse():
    assert markers.parse("a\n\n\n\n[[WRONG]]\n\n\nb").text == "a\n\nb"
