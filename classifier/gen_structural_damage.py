import json

data = [
    # Slot 1: english, complete
    {
        "id": "structural_damage_01",
        "incident_type": "structural_damage",
        "slot": 1,
        "lang_style": "english",
        "opening": "complete",
        "turns": [
            {
                "role": "user",
                "text": "A ceiling tile is hanging loose directly over the walkway in Corridor 4A on floor 4 of Building N, creating an immediate falling risk to pedestrians, noticed at 10:15 AM today.",
                "sentences": [
                    {"text": "A ceiling tile is hanging loose directly over the walkway in Corridor 4A on floor 4 of Building N, creating an immediate falling risk to pedestrians, noticed at 10:15 AM today.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Got it, I've alerted the civil carpentry and safety teams.",
                "asks": []
            }
        ],
        "final_fields": {
            "building": "N",
            "floor": 4,
            "space_type": "corridor",
            "space_id": "Corridor 4A",
            "occurred_at": "10:15 AM today",
            "damage_kind": "ceiling_tile",
            "falling_or_injury_risk": True
        },
        "final_priority": "P2"
    },
    # Slot 2: english, complete
    {
        "id": "structural_damage_02",
        "incident_type": "structural_damage",
        "slot": 2,
        "lang_style": "english",
        "opening": "complete",
        "turns": [
            {
                "role": "user",
                "text": "A decorative glass panel on the wall of Conference Room 2B on floor 2 of Building QRC has a hairline crack, with no falling or injury risk, spotted at 1:30 PM today.",
                "sentences": [
                    {"text": "A decorative glass panel on the wall of Conference Room 2B on floor 2 of Building QRC has a hairline crack, with no falling or injury risk, spotted at 1:30 PM today.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Understood, our carpentry team will inspect the glass panel.",
                "asks": []
            }
        ],
        "final_fields": {
            "building": "QRC",
            "floor": 2,
            "space_type": "conference_room",
            "space_id": "Conference Room 2B",
            "occurred_at": "1:30 PM today",
            "damage_kind": "broken_glass",
            "falling_or_injury_risk": False
        },
        "final_priority": "P3"
    },
    # Slot 3: english, complete
    {
        "id": "structural_damage_03",
        "incident_type": "structural_damage",
        "slot": 3,
        "lang_style": "english",
        "opening": "complete",
        "turns": [
            {
                "role": "user",
                "text": "The wooden hinge of the door in Pantry 5A on floor 5 of Building N is completely broken and jammed, no risk of falling or injury, noticed around 9:00 AM today.",
                "sentences": [
                    {"text": "The wooden hinge of the door in Pantry 5A on floor 5 of Building N is completely broken and jammed, no risk of falling or injury, noticed around 9:00 AM today.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Thanks, a carpentry technician has been dispatched to fix it.",
                "asks": []
            }
        ],
        "final_fields": {
            "building": "N",
            "floor": 5,
            "space_type": "pantry",
            "space_id": "Pantry 5A",
            "occurred_at": "9:00 AM today",
            "damage_kind": "broken_door",
            "falling_or_injury_risk": False
        },
        "final_priority": "P3"
    },
    # Slot 4: english, missing
    {
        "id": "structural_damage_04",
        "incident_type": "structural_damage",
        "slot": 4,
        "lang_style": "english",
        "opening": "missing",
        "turns": [
            {
                "role": "user",
                "text": "Hi, there is severe damage to the glass door entrance in Main Lobby on floor 1 of Building QRC.",
                "sentences": [
                    {"text": "Hi, there is severe damage to the glass door entrance in Main Lobby on floor 1 of Building QRC.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Could anything fall or hurt someone?",
                "asks": ["falling_or_injury_risk"]
            },
            {
                "role": "user",
                "text": "Yes, sharp shattered glass could cut people entering the building.",
                "sentences": [
                    {"text": "Yes, sharp shattered glass could cut people entering the building.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "What is the damaged item?",
                "asks": ["damage_kind"]
            },
            {
                "role": "user",
                "text": "It is broken glass from the swing door.",
                "sentences": [
                    {"text": "It is broken glass from the swing door.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "When did this break happen?",
                "asks": ["occurred_at"]
            },
            {
                "role": "user",
                "text": "Around 11:15 AM today.",
                "sentences": [
                    {"text": "Around 11:15 AM today.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Got it, emergency civil repairs and safety staff are dispatched.",
                "asks": []
            }
        ],
        "final_fields": {
            "building": "QRC",
            "floor": 1,
            "space_type": "lobby_reception",
            "space_id": "Main Lobby",
            "occurred_at": "11:15 AM today",
            "damage_kind": "broken_glass",
            "falling_or_injury_risk": True
        },
        "final_priority": "P2"
    },
    # Slot 5: english, missing (correction floor)
    {
        "id": "structural_damage_05",
        "incident_type": "structural_damage",
        "slot": 5,
        "lang_style": "english",
        "opening": "missing",
        "turns": [
            {
                "role": "user",
                "text": "There is a crack in the wall near Workstation Bay 3A in Building N, floor 2.",
                "sentences": [
                    {"text": "There is a crack in the wall near Workstation Bay 3A in Building N, floor 2.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Could anything fall or hurt someone?",
                "asks": ["falling_or_injury_risk"]
            },
            {
                "role": "user",
                "text": "Actually it is floor 3, not 2! No, no falling risk at all.",
                "sentences": [
                    {"text": "Actually it is floor 3, not 2!", "relevant": True},
                    {"text": "No, no falling risk at all.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "What is the damaged feature?",
                "asks": ["damage_kind"]
            },
            {
                "role": "user",
                "text": "It is a long wall crack near the column.",
                "sentences": [
                    {"text": "It is a long wall crack near the column.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Roughly when did you notice this?",
                "asks": ["occurred_at"]
            },
            {
                "role": "user",
                "text": "About 2:00 PM today.",
                "sentences": [
                    {"text": "About 2:00 PM today.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Thanks, the civil engineering team will inspect the crack.",
                "asks": []
            }
        ],
        "final_fields": {
            "building": "N",
            "floor": 3,
            "space_type": "workstation_bay",
            "space_id": "Workstation Bay 3A",
            "occurred_at": "2:00 PM today",
            "damage_kind": "wall_crack",
            "falling_or_injury_risk": False
        },
        "final_priority": "P3"
    },
    # Slot 6: english, missing (unknown risk)
    {
        "id": "structural_damage_06",
        "incident_type": "structural_damage",
        "slot": 6,
        "lang_style": "english",
        "opening": "missing",
        "turns": [
            {
                "role": "user",
                "text": "A ceiling tile is damaged in Stairwell 6 on floor 6 of Building N.",
                "sentences": [
                    {"text": "A ceiling tile is damaged in Stairwell 6 on floor 6 of Building N.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Could anything fall or hurt someone?",
                "asks": ["falling_or_injury_risk"]
            },
            {
                "role": "user",
                "text": "Not sure, it looks loose but hasn't dropped yet.",
                "sentences": [
                    {"text": "Not sure, it looks loose but hasn't dropped yet.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "When did you first notice this tile?",
                "asks": ["occurred_at"]
            },
            {
                "role": "user",
                "text": "Around 9:40 AM today.",
                "sentences": [
                    {"text": "Around 9:40 AM today.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Okay, carpentry technicians are dispatched immediately.",
                "asks": []
            }
        ],
        "final_fields": {
            "building": "N",
            "floor": 6,
            "space_type": "stairwell",
            "space_id": "Stairwell 6",
            "occurred_at": "9:40 AM today",
            "damage_kind": "ceiling_tile",
            "falling_or_injury_risk": "unknown"
        },
        "final_priority": "P2"
    },
    # Slot 7: english, missing
    {
        "id": "structural_damage_07",
        "incident_type": "structural_damage",
        "slot": 7,
        "lang_style": "english",
        "opening": "missing",
        "turns": [
            {
                "role": "user",
                "text": "A heavy wooden door won't latch shut in Building N, floor 7.",
                "sentences": [
                    {"text": "A heavy wooden door won't latch shut in Building N, floor 7.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Could anything fall or hurt someone?",
                "asks": ["falling_or_injury_risk"]
            },
            {
                "role": "user",
                "text": "No falling risk, it just swings loosely.",
                "sentences": [
                    {"text": "No falling risk, it just swings loosely.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Which room or area is the broken door in?",
                "asks": ["space_id"]
            },
            {
                "role": "user",
                "text": "It is Conference Room 7C.",
                "sentences": [
                    {"text": "It is Conference Room 7C.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "When did you notice it?",
                "asks": ["occurred_at"]
            },
            {
                "role": "user",
                "text": "Around 12:30 PM today.",
                "sentences": [
                    {"text": "Around 12:30 PM today.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Got it, carpentry staff will fix the broken door.",
                "asks": []
            }
        ],
        "final_fields": {
            "building": "N",
            "floor": 7,
            "space_type": "conference_room",
            "space_id": "Conference Room 7C",
            "occurred_at": "12:30 PM today",
            "damage_kind": "broken_door",
            "falling_or_injury_risk": False
        },
        "final_priority": "P3"
    },
    # Slot 8: english, missing
    {
        "id": "structural_damage_08",
        "incident_type": "structural_damage",
        "slot": 8,
        "lang_style": "english",
        "opening": "missing",
        "turns": [
            {
                "role": "user",
                "text": "There is noticeable floor damage with raised tiles that could trip people.",
                "sentences": [
                    {"text": "There is noticeable floor damage with raised tiles that could trip people.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Could anyone trip or get hurt?",
                "asks": ["falling_or_injury_risk"]
            },
            {
                "role": "user",
                "text": "Yes, several people almost tripped over it.",
                "sentences": [
                    {"text": "Yes, several people almost tripped over it.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Which building and floor is this?",
                "asks": ["building", "floor"]
            },
            {
                "role": "user",
                "text": "Building QRC, floor 3, by Elevator Lobby 3.",
                "sentences": [
                    {"text": "Building QRC, floor 3, by Elevator Lobby 3.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "When did you first spot this?",
                "asks": ["occurred_at"]
            },
            {
                "role": "user",
                "text": "About 3:10 PM today.",
                "sentences": [
                    {"text": "About 3:10 PM today.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Understood, civil carpentry technicians have been notified.",
                "asks": []
            }
        ],
        "final_fields": {
            "building": "QRC",
            "floor": 3,
            "space_type": "elevator_lobby",
            "space_id": "Elevator Lobby 3",
            "occurred_at": "3:10 PM today",
            "damage_kind": "floor_damage",
            "falling_or_injury_risk": True
        },
        "final_priority": "P2"
    },
    # Slot 9: english, missing
    {
        "id": "structural_damage_09",
        "incident_type": "structural_damage",
        "slot": 9,
        "lang_style": "english",
        "opening": "missing",
        "turns": [
            {
                "role": "user",
                "text": "A ceiling tile has caved in and fallen in RF Lab 9 on floor 9 of Building N.",
                "sentences": [
                    {"text": "A ceiling tile has caved in and fallen in RF Lab 9 on floor 9 of Building N.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Could anything else fall or injure someone?",
                "asks": ["falling_or_injury_risk"]
            },
            {
                "role": "user",
                "text": "Yes, adjacent ceiling tiles are sagging and about to fall.",
                "sentences": [
                    {"text": "Yes, adjacent ceiling tiles are sagging and about to fall.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Roughly when did this happen?",
                "asks": ["occurred_at"]
            },
            {
                "role": "user",
                "text": "Around 8:50 AM today.",
                "sentences": [
                    {"text": "Around 8:50 AM today.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Alerting civil maintenance and safety teams immediately.",
                "asks": []
            }
        ],
        "final_fields": {
            "building": "N",
            "floor": 9,
            "space_type": "rnd_lab",
            "space_id": "RF Lab 9",
            "occurred_at": "8:50 AM today",
            "damage_kind": "ceiling_tile",
            "falling_or_injury_risk": True
        },
        "final_priority": "P2"
    },
    # Slot 10: english, rambling
    {
        "id": "structural_damage_10",
        "incident_type": "structural_damage",
        "slot": 10,
        "lang_style": "english",
        "opening": "rambling",
        "turns": [
            {
                "role": "user",
                "text": "I lost my pen earlier and looked everywhere. Also, a floor tile is chipped and loose in Workstation Bay 10A on floor 10 of Building N.",
                "sentences": [
                    {"text": "I lost my pen earlier and looked everywhere.", "relevant": False},
                    {"text": "Also, a floor tile is chipped and loose in Workstation Bay 10A on floor 10 of Building N.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Could anything fall or hurt someone?",
                "asks": ["falling_or_injury_risk"]
            },
            {
                "role": "user",
                "text": "I should buy a new notebook too. Yes, the edge is sharp enough to cut someone's foot.",
                "sentences": [
                    {"text": "I should buy a new notebook too.", "relevant": False},
                    {"text": "Yes, the edge is sharp enough to cut someone's foot.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "When did you first notice this floor damage?",
                "asks": ["occurred_at"]
            },
            {
                "role": "user",
                "text": "Around 10:30 AM today.",
                "sentences": [
                    {"text": "Around 10:30 AM today.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Got it, carpentry repairs have been notified.",
                "asks": []
            }
        ],
        "final_fields": {
            "building": "N",
            "floor": 10,
            "space_type": "workstation_bay",
            "space_id": "Workstation Bay 10A",
            "occurred_at": "10:30 AM today",
            "damage_kind": "floor_damage",
            "falling_or_injury_risk": True
        },
        "final_priority": "P2"
    },
    # Slot 11: english, rambling (correction on space_id)
    {
        "id": "structural_damage_11",
        "incident_type": "structural_damage",
        "slot": 11,
        "lang_style": "english",
        "opening": "rambling",
        "turns": [
            {
                "role": "user",
                "text": "I am so tired of endless status calls. There is a broken door near Pantry 4A on floor 4 of Building QRC.",
                "sentences": [
                    {"text": "I am so tired of endless status calls.", "relevant": False},
                    {"text": "There is a broken door near Pantry 4A on floor 4 of Building QRC.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Could anything fall or hurt someone?",
                "asks": ["falling_or_injury_risk"]
            },
            {
                "role": "user",
                "text": "Actually it is Pantry 4B, my mistake. No, no risk of injury or falling.",
                "sentences": [
                    {"text": "Actually it is Pantry 4B, my mistake.", "relevant": True},
                    {"text": "No, no risk of injury or falling.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Roughly when did you notice the broken door?",
                "asks": ["occurred_at"]
            },
            {
                "role": "user",
                "text": "About 1:15 PM today.",
                "sentences": [
                    {"text": "About 1:15 PM today.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Thanks, the carpentry team will repair the door.",
                "asks": []
            }
        ],
        "final_fields": {
            "building": "QRC",
            "floor": 4,
            "space_type": "pantry",
            "space_id": "Pantry 4B",
            "occurred_at": "1:15 PM today",
            "damage_kind": "broken_door",
            "falling_or_injury_risk": False
        },
        "final_priority": "P3"
    },
    # Slot 12: english, rambling (unknown answer)
    {
        "id": "structural_damage_12",
        "incident_type": "structural_damage",
        "slot": 12,
        "lang_style": "english",
        "opening": "rambling",
        "turns": [
            {
                "role": "user",
                "text": "The air quality feels poor today. There is building damage in Corridor 12A on floor 12 of Building N.",
                "sentences": [
                    {"text": "The air quality feels poor today.", "relevant": False},
                    {"text": "There is building damage in Corridor 12A on floor 12 of Building N.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Could anything fall or hurt someone?",
                "asks": ["falling_or_injury_risk"]
            },
            {
                "role": "user",
                "text": "I really don't know, there are loose plaster pieces near the roof.",
                "sentences": [
                    {"text": "I really don't know, there are loose plaster pieces near the roof.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "What is the damaged feature?",
                "asks": ["damage_kind"]
            },
            {
                "role": "user",
                "text": "It is a ceiling tile cracking apart.",
                "sentences": [
                    {"text": "It is a ceiling tile cracking apart.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "When did you first notice this?",
                "asks": ["occurred_at"]
            },
            {
                "role": "user",
                "text": "Around 3:45 PM today.",
                "sentences": [
                    {"text": "Around 3:45 PM today.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Understood, safety and civil teams will inspect it.",
                "asks": []
            }
        ],
        "final_fields": {
            "building": "N",
            "floor": 12,
            "space_type": "corridor",
            "space_id": "Corridor 12A",
            "occurred_at": "3:45 PM today",
            "damage_kind": "ceiling_tile",
            "falling_or_injury_risk": "unknown"
        },
        "final_priority": "P2"
    },
    # Slot 13: hindi_translated, complete
    {
        "id": "structural_damage_13",
        "incident_type": "structural_damage",
        "slot": 13,
        "lang_style": "hindi_translated",
        "opening": "complete",
        "turns": [
            {
                "role": "user",
                "text": "In Building QRC on floor 5, wall crack is seen in Cafeteria 2, no falling danger to anyone, noticed at 11:30 AM today morning.",
                "sentences": [
                    {"text": "In Building QRC on floor 5, wall crack is seen in Cafeteria 2, no falling danger to anyone, noticed at 11:30 AM today morning.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Understood, civil carpentry technicians will examine the wall crack.",
                "asks": []
            }
        ],
        "final_fields": {
            "building": "QRC",
            "floor": 5,
            "space_type": "cafeteria",
            "space_id": "Cafeteria 2",
            "occurred_at": "11:30 AM today",
            "damage_kind": "wall_crack",
            "falling_or_injury_risk": False
        },
        "final_priority": "P3"
    },
    # Slot 14: hindi_translated, missing
    {
        "id": "structural_damage_14",
        "incident_type": "structural_damage",
        "slot": 14,
        "lang_style": "hindi_translated",
        "opening": "missing",
        "turns": [
            {
                "role": "user",
                "text": "In Building N, floor 2, Corridor 2B ceiling tile is hanging downwards.",
                "sentences": [
                    {"text": "In Building N, floor 2, Corridor 2B ceiling tile is hanging downwards.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Could anything fall or hurt someone?",
                "asks": ["falling_or_injury_risk"]
            },
            {
                "role": "user",
                "text": "Yes, tile is about to fall on someone walking.",
                "sentences": [
                    {"text": "Yes, tile is about to fall on someone walking.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "At what time did you see this?",
                "asks": ["occurred_at"]
            },
            {
                "role": "user",
                "text": "Around 12:15 PM today afternoon.",
                "sentences": [
                    {"text": "Around 12:15 PM today afternoon.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Got it, civil repair team has been dispatched.",
                "asks": []
            }
        ],
        "final_fields": {
            "building": "N",
            "floor": 2,
            "space_type": "corridor",
            "space_id": "Corridor 2B",
            "occurred_at": "12:15 PM today",
            "damage_kind": "ceiling_tile",
            "falling_or_injury_risk": True
        },
        "final_priority": "P2"
    },
    # Slot 15: hindi_translated, missing
    {
        "id": "structural_damage_15",
        "incident_type": "structural_damage",
        "slot": 15,
        "lang_style": "hindi_translated",
        "opening": "missing",
        "turns": [
            {
                "role": "user",
                "text": "In Building QRC, floor 6, Workstation Bay 6A door lock and frame is broken.",
                "sentences": [
                    {"text": "In Building QRC, floor 6, Workstation Bay 6A door lock and frame is broken.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Could anything fall or hurt someone?",
                "asks": ["falling_or_injury_risk"]
            },
            {
                "role": "user",
                "text": "No, it is broken door only, no danger of hurting.",
                "sentences": [
                    {"text": "No, it is broken door only, no danger of hurting.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "When did you notice this broken door?",
                "asks": ["occurred_at"]
            },
            {
                "role": "user",
                "text": "Around 9:10 AM today morning.",
                "sentences": [
                    {"text": "Around 9:10 AM today morning.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Thanks, a carpentry technician will repair the door.",
                "asks": []
            }
        ],
        "final_fields": {
            "building": "QRC",
            "floor": 6,
            "space_type": "workstation_bay",
            "space_id": "Workstation Bay 6A",
            "occurred_at": "9:10 AM today",
            "damage_kind": "broken_door",
            "falling_or_injury_risk": False
        },
        "final_priority": "P3"
    },
    # Slot 16: hindi_translated, rambling
    {
        "id": "structural_damage_16",
        "incident_type": "structural_damage",
        "slot": 16,
        "lang_style": "hindi_translated",
        "opening": "rambling",
        "turns": [
            {
                "role": "user",
                "text": "Today my laptop bag zipper broke. In Building N, floor 8, Loading Dock 1 floor damage has occurred.",
                "sentences": [
                    {"text": "Today my laptop bag zipper broke.", "relevant": False},
                    {"text": "In Building N, floor 8, Loading Dock 1 floor damage has occurred.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Could anyone trip or get hurt?",
                "asks": ["falling_or_injury_risk"]
            },
            {
                "role": "user",
                "text": "Yes, concrete is cracked deep, trolley can overturn and injure.",
                "sentences": [
                    {"text": "Yes, concrete is cracked deep, trolley can overturn and injure.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "When did you first notice this?",
                "asks": ["occurred_at"]
            },
            {
                "role": "user",
                "text": "Around 2:20 PM today.",
                "sentences": [
                    {"text": "Around 2:20 PM today.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Okay, civil maintenance and safety teams have been informed.",
                "asks": []
            }
        ],
        "final_fields": {
            "building": "N",
            "floor": 8,
            "space_type": "loading_dock",
            "space_id": "Loading Dock 1",
            "occurred_at": "2:20 PM today",
            "damage_kind": "floor_damage",
            "falling_or_injury_risk": True
        },
        "final_priority": "P2"
    },
    # Slot 17: hinglish, missing
    {
        "id": "structural_damage_17",
        "incident_type": "structural_damage",
        "slot": 17,
        "lang_style": "hinglish",
        "opening": "missing",
        "turns": [
            {
                "role": "user",
                "text": "Building N ke floor 3 pe Conference Room 3C ka glass partition broken glass ho gaya hai crack hoke.",
                "sentences": [
                    {"text": "Building N ke floor 3 pe Conference Room 3C ka glass partition broken glass ho gaya hai crack hoke.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Could anything fall or hurt someone?",
                "asks": ["falling_or_injury_risk"]
            },
            {
                "role": "user",
                "text": "Haan, pieces gir sakte hain and injury ho sakti hai.",
                "sentences": [
                    {"text": "Haan, pieces gir sakte hain and injury ho sakti hai.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Roughly when did this happen?",
                "asks": ["occurred_at"]
            },
            {
                "role": "user",
                "text": "Around 1:40 PM notice kiya.",
                "sentences": [
                    {"text": "Around 1:40 PM notice kiya.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Got it, carpentry and safety teams are on the way.",
                "asks": []
            }
        ],
        "final_fields": {
            "building": "N",
            "floor": 3,
            "space_type": "conference_room",
            "space_id": "Conference Room 3C",
            "occurred_at": "1:40 PM today",
            "damage_kind": "broken_glass",
            "falling_or_injury_risk": True
        },
        "final_priority": "P2"
    },
    # Slot 18: hinglish, complete
    {
        "id": "structural_damage_18",
        "incident_type": "structural_damage",
        "slot": 18,
        "lang_style": "hinglish",
        "opening": "complete",
        "turns": [
            {
                "role": "user",
                "text": "Building QRC, floor 2 ke Stairwell 2 mein ceiling tile loose ho ke latak raha hai, falling risk hai logo ke liye, 10:50 AM pe dekha.",
                "sentences": [
                    {"text": "Building QRC, floor 2 ke Stairwell 2 mein ceiling tile loose ho ke latak raha hai, falling risk hai logo ke liye, 10:50 AM pe dekha.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Civil carpentry and safety teams are attending to this immediately.",
                "asks": []
            }
        ],
        "final_fields": {
            "building": "QRC",
            "floor": 2,
            "space_type": "stairwell",
            "space_id": "Stairwell 2",
            "occurred_at": "10:50 AM today",
            "damage_kind": "ceiling_tile",
            "falling_or_injury_risk": True
        },
        "final_priority": "P2"
    }
]

with open("data/structural_damage.jsonl", "w", encoding="utf-8") as f:
    for row in data:
        f.write(json.dumps(row) + "\n")

print("Generated structural_damage.jsonl successfully")
