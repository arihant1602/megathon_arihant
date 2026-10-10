"""Validate imported Laya labels and dialogue policy without downloading weights.

These checks cover the checkpoint/schema boundary and deterministic policy;
they do not measure model inference or Android runtime behavior.
"""
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "classifier/laya"))
import form
from llm import validate


class LayaIntegrationTest(unittest.TestCase):
    def test_checkpoint_matches_android_schema(self):
        import torch
        from common import L, LAYA_REVISION, label_space
        checkpoint = torch.load(ROOT / "classifier/laya_ft.pt", map_location="cpu", weights_only=True)
        _, types, fields = label_space()
        self.assertEqual(checkpoint["types"], types)
        self.assertEqual(checkpoint["fields"], fields)
        self.assertEqual(checkpoint["L"], L)
        self.assertEqual(checkpoint["laya_revision"], LAYA_REVISION)
        self.assertGreater(checkpoint["temperature"], 0)

    def test_uncertain_type_requires_clarification(self):
        state = form.FormState()
        probabilities = {"water_leak": .45, "washroom_plumbing": .4, "undetermined": .15}
        form.settle_type(state, probabilities, .8)
        self.assertIsNone(state.type)
        self.assertFalse(form.next_action(state, probabilities, .8, 1)["kind"] == "submit")
        self.assertFalse(state.done)

    def test_done_cannot_skip_missing_location(self):
        state = form.FormState()
        state.type = "water_leak"
        action = form.next_action(state, {"water_leak": .95}, .8, 1, ended=True)
        self.assertFalse(state.done)
        self.assertEqual(action["fields"], ["building", "floor"])

    def test_confident_correction_changes_type(self):
        state = form.FormState()
        state.type = "water_leak"
        form.settle_type(state, {"power_outage": .94, "water_leak": .06}, .8)
        self.assertEqual(state.type, "power_outage")

    def test_out_of_scope_does_not_select_form(self):
        state = form.FormState()
        action = form.next_action(state, {"none": .96, "water_leak": .04}, .8, 1)
        self.assertEqual(action["kind"], "out_of_scope")
        self.assertIsNone(state.type)

    def test_extraction_rejects_unsupported_values(self):
        fields = [form.field_meta(name) for name in ("water_spread", "space_id", "floor")]
        result = validate({"water_spread": "flooding", "space_id": "Room 9 basement", "floor": "4"},
                          fields, "the pantry on floor 4")
        self.assertEqual(result, {"floor": 4})


if __name__ == "__main__":
    unittest.main()
