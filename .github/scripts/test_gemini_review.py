"""네트워크와 대기 없이 실제 main의 재시도 및 코멘트 처리를 검증한다."""

import contextlib
import io
import json
import os
import unittest
from unittest.mock import patch
import urllib.error

import gemini_review as review


class GeminiReviewTests(unittest.TestCase):
    SHA = "a" * 40
    PRIVATE_TEXT = "fake-private-value-never-log"
    SUCCESS = {"candidates": [{
        "content": {"parts": [{"text": "src/Fake.java:7 소유자 검사가 필요합니다."}]},
        "finishReason": "STOP",
    }]}

    def http_error(self, status):
        return urllib.error.HTTPError(
            "https://example.invalid", status, self.PRIVATE_TEXT, {},
            io.BytesIO(self.PRIVATE_TEXT.encode()),
        )

    def run_scenario(self, events):
        clock = 0
        requests, comments, gh_calls = [], [], []
        events = iter(events)

        def fake_urlopen(request, timeout):
            nonlocal clock
            self.assertEqual(timeout, 240)
            requests.append(json.loads(request.data))
            event = next(events)
            is_timeout = isinstance(event, TimeoutError) or (
                isinstance(event, urllib.error.URLError) and isinstance(event.reason, TimeoutError)
            )
            clock += 240 if is_timeout else 1
            if isinstance(event, Exception):
                raise event
            response = io.BytesIO(event if isinstance(event, bytes) else json.dumps(event).encode())
            response.status = 200
            return response

        def fake_sleep(seconds):
            nonlocal clock
            clock += seconds

        def fake_gh(*args, payload=None):
            gh_calls.append(args)
            if args[:2] == ("pr", "view"):
                return json.dumps({
                    "headRefOid": self.SHA, "title": "fix: 테스트", "body": "Closes #19",
                    "commits": [{"messageHeadline": "fix: 테스트", "messageBody": "Refs #19"}],
                })
            if args[:2] == ("pr", "diff"):
                return "diff --git a/src/Fake.java b/src/Fake.java\n@@ -7 +7 @@\n-checkOwner();\n+save();"
            if args[0] == "api" and "/contents/" in args[1]:
                return "저장소 기준 규칙"
            if "--slurp" in args:
                return "[[]]"
            if "--method" in args:
                comments.append(payload["body"])
                return "{}"
            self.fail(f"예상하지 않은 gh 호출: {args}")

        env = {
            "GITHUB_REPOSITORY": "DOKPAMINE/DOKPAMINE-BE", "PR_NUMBER": "19",
            "REVIEW_SHA": self.SHA, "BASE_SHA": "b" * 40,
            "GEMINI_MODEL": "gemini-3.8-flash", "GEMINI_API_KEY": self.PRIVATE_TEXT,
        }
        logs = io.StringIO()
        with patch.dict(os.environ, env, clear=True), \
             patch.object(review, "gh", side_effect=fake_gh), \
             patch.object(review.subprocess, "run", side_effect=AssertionError("네트워크 금지")), \
             patch.object(review.urllib.request, "urlopen", side_effect=fake_urlopen), \
             patch.object(review.time, "monotonic", side_effect=lambda: clock), \
             patch.object(review.time, "sleep", side_effect=fake_sleep) as sleep, \
             contextlib.redirect_stdout(logs):
            review.main()
        self.assertEqual(len(comments), 1)
        self.assertIn(review.MARKER, comments[0])
        self.assertIn(self.SHA, comments[0])
        self.assertNotIn(self.PRIVATE_TEXT, comments[0] + logs.getvalue())
        return requests, comments[0], logs.getvalue(), sleep

    def test_503_then_success_posts_review(self):
        requests, comment, logs, sleep = self.run_scenario([self.http_error(503), self.SUCCESS])
        self.assertEqual(len(requests), 2)
        self.assertIn("src/Fake.java:7", comment)
        self.assertNotIn("리뷰를 건너뜀", comment)
        self.assertIn("HTTPError · HTTP 503 · 시도 1회 · 경과 1.0초", logs)
        sleep.assert_called_once_with(20)

    def test_500_then_success_retries_once(self):
        requests, _, _, sleep = self.run_scenario([self.http_error(500), self.SUCCESS])
        self.assertEqual(len(requests), 2)
        sleep.assert_called_once_with(20)

    def test_two_timeouts_skip_with_diagnostics(self):
        requests, comment, logs, sleep = self.run_scenario([
            TimeoutError(self.PRIVATE_TEXT), TimeoutError(self.PRIVATE_TEXT),
        ])
        self.assertEqual(len(requests), 2)
        for output in (comment, logs):
            self.assertIn("TimeoutError · HTTP 응답 없음 · 시도 2회 · 경과 500.0초", output)
        self.assertIn("리뷰를 건너뜀", comment)
        sleep.assert_called_once_with(20)

    def test_wrapped_timeout_retries_and_identifies_urlerror(self):
        requests, comment, _, _ = self.run_scenario([
            urllib.error.URLError(TimeoutError(self.PRIVATE_TEXT)),
            urllib.error.URLError(TimeoutError(self.PRIVATE_TEXT)),
        ])
        self.assertEqual(len(requests), 2)
        self.assertIn("URLError(원인 TimeoutError)", comment)

    def test_429_skips_without_retry(self):
        requests, comment, logs, sleep = self.run_scenario([self.http_error(429)])
        self.assertEqual(len(requests), 1)
        for output in (comment, logs):
            self.assertIn("HTTPError · HTTP 429 · 시도 1회 · 경과 1.0초", output)
        self.assertIn("무료 등급 한도", comment)
        sleep.assert_not_called()

    def test_request_uses_low_thinking_and_preserves_output_budget(self):
        requests, _, _, _ = self.run_scenario([self.SUCCESS])
        config = requests[0]["generationConfig"]
        self.assertEqual(config["thinkingConfig"], {"thinkingLevel": "low"})
        self.assertEqual(config["maxOutputTokens"], 16384)

    def test_success_does_not_retry(self):
        requests, comment, logs, sleep = self.run_scenario([self.SUCCESS])
        self.assertEqual(len(requests), 1)
        self.assertIn("src/Fake.java:7", comment)
        self.assertEqual(logs, "")
        sleep.assert_not_called()

    def test_empty_response_skips_with_diagnostics_without_retry(self):
        requests, comment, logs, sleep = self.run_scenario([{"candidates": []}])
        self.assertEqual(len(requests), 1)
        self.assertIn("빈 응답", comment)
        for output in (comment, logs):
            self.assertIn("HTTP 200 · 시도 1회 · 경과 1.0초", output)
        sleep.assert_not_called()

    def test_safety_block_remains_visible_without_retry(self):
        requests, comment, _, sleep = self.run_scenario([{
            "candidates": [{"finishReason": "SAFETY", "content": None}],
        }])
        self.assertEqual(len(requests), 1)
        self.assertIn("빈 응답(SAFETY)", comment)
        sleep.assert_not_called()

    def test_prompt_safety_block_remains_visible(self):
        _, comment, _, _ = self.run_scenario([{"promptFeedback": {"blockReason": "SAFETY"}}])
        self.assertIn("빈 응답(SAFETY)", comment)

    def test_400_falls_back_without_thinking_config_once(self):
        requests, comment, logs, sleep = self.run_scenario([self.http_error(400), self.SUCCESS])
        self.assertEqual(len(requests), 2)
        self.assertIn("thinkingConfig", requests[0]["generationConfig"])
        self.assertNotIn("thinkingConfig", requests[1]["generationConfig"])
        self.assertIn("thinkingConfig 없이", logs)
        self.assertIn("src/Fake.java:7", comment)
        sleep.assert_not_called()

    def test_400_fallback_also_consumes_retry_budget(self):
        requests, comment, _, sleep = self.run_scenario([self.http_error(400), self.http_error(503)])
        self.assertEqual(len(requests), 2)
        self.assertIn("HTTP 503 · 시도 2회", comment)
        sleep.assert_not_called()

    def test_repeated_503_stops_at_two_attempts(self):
        requests, comment, _, sleep = self.run_scenario([self.http_error(503), self.http_error(503)])
        self.assertEqual(len(requests), 2)
        self.assertIn("HTTP 503 · 시도 2회", comment)
        sleep.assert_called_once_with(20)

    def test_non_timeout_urlerror_does_not_retry(self):
        requests, comment, _, sleep = self.run_scenario([urllib.error.URLError(self.PRIVATE_TEXT)])
        self.assertEqual(len(requests), 1)
        self.assertIn("URLError · HTTP 응답 없음 · 시도 1회", comment)
        sleep.assert_not_called()

    def test_malformed_json_does_not_retry_or_expose_response(self):
        requests, comment, _, sleep = self.run_scenario([self.PRIVATE_TEXT.encode()])
        self.assertEqual(len(requests), 1)
        self.assertIn("JSONDecodeError · HTTP 200 · 시도 1회", comment)
        sleep.assert_not_called()


if __name__ == "__main__":
    unittest.main(verbosity=2)
