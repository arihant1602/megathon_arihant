import json

data = [
    # Slot 1: english, complete
    {
        "id": "electrical_hazard_01",
        "incident_type": "electrical_hazard",
        "slot": 1,
        "lang_style": "english",
        "opening": "complete",
        "turns": [
            {
                "role": "user",
                "text": "There are visible sparks shooting from a wall outlet in Workstation Bay 4A on floor 4 of Building N, no smoke present, noticed at 10:15 AM today.",
                "sentences": [
                    {"text": "There are visible sparks shooting from a wall outlet in Workstation Bay 4A on floor 4 of Building N, no smoke present, noticed at 10:15 AM today.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Emergency electricians and safety engineers are dispatched immediately.",
                "asks": []
            }
        ],
        "final_fields": {
            "building": "N",
            "floor": 4,
            "space_type": "workstation_bay",
            "space_id": "Workstation Bay 4A",
            "occurred_at": "10:15 AM today",
            "hazard_kind": "sparks",
            "smoke_present": False
        },
        "final_priority": "P1"
    },
    # Slot 2: english, complete
    {
        "id": "electrical_hazard_02",
        "incident_type": "electrical_hazard",
        "slot": 2,
        "lang_style": "english",
        "opening": "complete",
        "turns": [
            {
                "role": "user",
                "text": "Strong burning smell and black smoke coming from a socket in Conference Room 2B, floor 2, Building QRC at 1:40 PM today.",
                "sentences": [
                    {"text": "Strong burning smell and black smoke coming from a socket in Conference Room 2B, floor 2, Building QRC at 1:40 PM today.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Evacuate the room, emergency electricians are on the way.",
                "asks": []
            }
        ],
        "final_fields": {
            "building": "QRC",
            "floor": 2,
            "space_type": "conference_room",
            "space_id": "Conference Room 2B",
            "occurred_at": "1:40 PM today",
            "hazard_kind": "burning_smell",
            "smoke_present": True
        },
        "final_priority": "P1"
    },
    # Slot 3: english, complete
    {
        "id": "electrical_hazard_03",
        "incident_type": "electrical_hazard",
        "slot": 3,
        "lang_style": "english",
        "opening": "complete",
        "turns": [
            {
                "role": "user",
                "text": "An exposed live wire is dangling by the door of RF Lab 5 on floor 5 of Building N, without any smoke, seen around 8:30 AM today.",
                "sentences": [
                    {"text": "An exposed live wire is dangling by the door of RF Lab 5 on floor 5 of Building N, without any smoke, seen around 8:30 AM today.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Understood, electrical technicians have been alerted immediately.",
                "asks": []
            }
        ],
        "final_fields": {
            "building": "N",
            "floor": 5,
            "space_type": "rnd_lab",
            "space_id": "RF Lab 5",
            "occurred_at": "8:30 AM today",
            "hazard_kind": "exposed_wire",
            "smoke_present": False
        },
        "final_priority": "P1"
    },
    # Slot 4: english, missing
    {
        "id": "electrical_hazard_04",
        "incident_type": "electrical_hazard",
        "slot": 4,
        "lang_style": "english",
        "opening": "missing",
        "turns": [
            {
                "role": "user",
                "text": "There is a burning electrical smell in Pantry 3A on floor 3 of Building N.",
                "sentences": [
                    {"text": "There is a burning electrical smell in Pantry 3A on floor 3 of Building N.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Is there any smoke present?",
                "asks": ["smoke_present"]
            },
            {
                "role": "user",
                "text": "No smoke visible, just a strong pungent burning smell.",
                "sentences": [
                    {"text": "No smoke visible, just a strong pungent burning smell.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "When did you first notice this?",
                "asks": ["occurred_at"]
            },
            {
                "role": "user",
                "text": "Around 11:10 AM today.",
                "sentences": [
                    {"text": "Around 11:10 AM today.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Got it, emergency electricians are on their way.",
                "asks": []
            }
        ],
        "final_fields": {
            "building": "N",
            "floor": 3,
            "space_type": "pantry",
            "space_id": "Pantry 3A",
            "occurred_at": "11:10 AM today",
            "hazard_kind": "burning_smell",
            "smoke_present": False
        },
        "final_priority": "P1"
    },
    # Slot 5: english, missing (correction floor)
    {
        "id": "electrical_hazard_05",
        "incident_type": "electrical_hazard",
        "slot": 5,
        "lang_style": "english",
        "opening": "missing",
        "turns": [
            {
                "role": "user",
                "text": "A power outlet is sparking in Building QRC, floor 2, Workstation Bay 2B.",
                "sentences": [
                    {"text": "A power outlet is sparking in Building QRC, floor 2, Workstation Bay 2B.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Is there any smoke?",
                "asks": ["smoke_present"]
            },
            {
                "role": "user",
                "text": "Actually it's floor 3, not 2! Yes, grey smoke is puffing out.",
                "sentences": [
                    {"text": "Actually it's floor 3, not 2!", "relevant": True},
                    {"text": "Yes, grey smoke is puffing out.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "When did this start happening?",
                "asks": ["occurred_at"]
            },
            {
                "role": "user",
                "text": "Just now, around 2:15 PM.",
                "sentences": [
                    {"text": "Just now, around 2:15 PM.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Please stand back, electrical and safety teams are dispatched.",
                "asks": []
            }
        ],
        "final_fields": {
            "building": "QRC",
            "floor": 3,
            "space_type": "workstation_bay",
            "space_id": "Workstation Bay 2B",
            "occurred_at": "2:15 PM today",
            "hazard_kind": "sparks",
            "smoke_present": True
        },
        "final_priority": "P1"
    },
    # Slot 6: english, missing (unknown smoke)
    {
        "id": "electrical_hazard_06",
        "incident_type": "electrical_hazard",
        "slot": 6,
        "lang_style": "english",
        "opening": "missing",
        "turns": [
            {
                "role": "user",
                "text": "An employee felt a mild electric shock from a switchboard in Server Room 6A, floor 6, Building N.",
                "sentences": [
                    {"text": "An employee felt a mild electric shock from a switchboard in Server Room 6A, floor 6, Building N.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Is there any smoke?",
                "asks": ["smoke_present"]
            },
            {
                "role": "user",
                "text": "I am not sure, we closed the door immediately.",
                "sentences": [
                    {"text": "I am not sure, we closed the door immediately.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "When did the shock happen?",
                "asks": ["occurred_at"]
            },
            {
                "role": "user",
                "text": "Around 9:50 AM today.",
                "sentences": [
                    {"text": "Around 9:50 AM today.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Electricians and safety responders are en route now.",
                "asks": []
            }
        ],
        "final_fields": {
            "building": "N",
            "floor": 6,
            "space_type": "server_or_idf_room",
            "space_id": "Server Room 6A",
            "occurred_at": "9:50 AM today",
            "hazard_kind": "shock",
            "smoke_present": "unknown"
        },
        "final_priority": "P1"
    },
    # Slot 7: english, missing
    {
        "id": "electrical_hazard_07",
        "incident_type": "electrical_hazard",
        "slot": 7,
        "lang_style": "english",
        "opening": "missing",
        "turns": [
            {
                "role": "user",
                "text": "The wall socket feels extremely hot to the touch in Cafeteria 1 on floor 1 of Building N.",
                "sentences": [
                    {"text": "The wall socket feels extremely hot to the touch in Cafeteria 1 on floor 1 of Building N.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Is there any smoke coming from it?",
                "asks": ["smoke_present"]
            },
            {
                "role": "user",
                "text": "No smoke, but the faceplate is scorched hot.",
                "sentences": [
                    {"text": "No smoke, but the faceplate is scorched hot.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "When did you first notice this?",
                "asks": ["occurred_at"]
            },
            {
                "role": "user",
                "text": "Around 12:20 PM today.",
                "sentences": [
                    {"text": "Around 12:20 PM today.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Okay, the electrical team will inspect it immediately.",
                "asks": []
            }
        ],
        "final_fields": {
            "building": "N",
            "floor": 1,
            "space_type": "cafeteria",
            "space_id": "Cafeteria 1",
            "occurred_at": "12:20 PM today",
            "hazard_kind": "hot_outlet",
            "smoke_present": False
        },
        "final_priority": "P1"
    },
    # Slot 8: english, missing
    {
        "id": "electrical_hazard_08",
        "incident_type": "electrical_hazard",
        "slot": 8,
        "lang_style": "english",
        "opening": "missing",
        "turns": [
            {
                "role": "user",
                "text": "There are open exposed wires sticking out near the elevator bank.",
                "sentences": [
                    {"text": "There are open exposed wires sticking out near the elevator bank.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Is there any smoke present?",
                "asks": ["smoke_present"]
            },
            {
                "role": "user",
                "text": "No smoke at all, just bare wires.",
                "sentences": [
                    {"text": "No smoke at all, just bare wires.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Which building and floor is this?",
                "asks": ["building", "floor"]
            },
            {
                "role": "user",
                "text": "Building QRC, floor 5, by Elevator Lobby 5.",
                "sentences": [
                    {"text": "Building QRC, floor 5, by Elevator Lobby 5.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "When did you notice this hazard?",
                "asks": ["occurred_at"]
            },
            {
                "role": "user",
                "text": "About 3:00 PM today.",
                "sentences": [
                    {"text": "About 3:00 PM today.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Thanks, electricians are on their way.",
                "asks": []
            }
        ],
        "final_fields": {
            "building": "QRC",
            "floor": 5,
            "space_type": "elevator_lobby",
            "space_id": "Elevator Lobby 5",
            "occurred_at": "3:00 PM today",
            "hazard_kind": "exposed_wire",
            "smoke_present": False
        },
        "final_priority": "P1"
    },
    # Slot 9: english, missing
    {
        "id": "electrical_hazard_09",
        "incident_type": "electrical_hazard",
        "slot": 9,
        "lang_style": "english",
        "opening": "missing",
        "turns": [
            {
                "role": "user",
                "text": "We noticed an electrical hazard in Corridor 9A on floor 9 of Building N.",
                "sentences": [
                    {"text": "We noticed an electrical hazard in Corridor 9A on floor 9 of Building N.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Is there any smoke?",
                "asks": ["smoke_present"]
            },
            {
                "role": "user",
                "text": "No smoke, but a junction box is crackling and sparking.",
                "sentences": [
                    {"text": "No smoke, but a junction box is crackling and sparking.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Roughly when did this happen?",
                "asks": ["occurred_at"]
            },
            {
                "role": "user",
                "text": "Around 1:15 PM today.",
                "sentences": [
                    {"text": "Around 1:15 PM today.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Got it, electrical technicians have been sent.",
                "asks": []
            }
        ],
        "final_fields": {
            "building": "N",
            "floor": 9,
            "space_type": "corridor",
            "space_id": "Corridor 9A",
            "occurred_at": "1:15 PM today",
            "hazard_kind": "sparks",
            "smoke_present": False
        },
        "final_priority": "P1"
    },
    # Slot 10: english, rambling
    {
        "id": "electrical_hazard_10",
        "incident_type": "electrical_hazard",
        "slot": 10,
        "lang_style": "english",
        "opening": "rambling",
        "turns": [
            {
                "role": "user",
                "text": "I spilled tea on my notes earlier today. Also, there are sparks coming from an outlet in Workstation Bay 12B on floor 12 of Building N.",
                "sentences": [
                    {"text": "I spilled tea on my notes earlier today.", "relevant": False},
                    {"text": "Also, there are sparks coming from an outlet in Workstation Bay 12B on floor 12 of Building N.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Is there any smoke?",
                "asks": ["smoke_present"]
            },
            {
                "role": "user",
                "text": "I really need a break. No, there is no smoke.",
                "sentences": [
                    {"text": "I really need a break.", "relevant": False},
                    {"text": "No, there is no smoke.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "When did you first notice the sparks?",
                "asks": ["occurred_at"]
            },
            {
                "role": "user",
                "text": "Around 10:05 AM today.",
                "sentences": [
                    {"text": "Around 10:05 AM today.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Electricians have been alerted to attend to this.",
                "asks": []
            }
        ],
        "final_fields": {
            "building": "N",
            "floor": 12,
            "space_type": "workstation_bay",
            "space_id": "Workstation Bay 12B",
            "occurred_at": "10:05 AM today",
            "hazard_kind": "sparks",
            "smoke_present": False
        },
        "final_priority": "P1"
    },
    # Slot 11: english, rambling (correction on space_id)
    {
        "id": "electrical_hazard_11",
        "incident_type": "electrical_hazard",
        "slot": 11,
        "lang_style": "english",
        "opening": "rambling",
        "turns": [
            {
                "role": "user",
                "text": "Bangalore traffic was unusually heavy. In Building QRC floor 1, there is a burning smell near Security Desk 1.",
                "sentences": [
                    {"text": "Bangalore traffic was unusually heavy.", "relevant": False},
                    {"text": "In Building QRC floor 1, there is a burning smell near Security Desk 1.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Is there any smoke present?",
                "asks": ["smoke_present"]
            },
            {
                "role": "user",
                "text": "Actually it is Lobby Reception 1, right beside it. Yes, slight smoke is drifting.",
                "sentences": [
                    {"text": "Actually it is Lobby Reception 1, right beside it.", "relevant": True},
                    {"text": "Yes, slight smoke is drifting.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Roughly when did you notice the smell?",
                "asks": ["occurred_at"]
            },
            {
                "role": "user",
                "text": "Around 8:40 AM today.",
                "sentences": [
                    {"text": "Around 8:40 AM today.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Emergency electricians and safety staff have been dispatched.",
                "asks": []
            }
        ],
        "final_fields": {
            "building": "QRC",
            "floor": 1,
            "space_type": "lobby_reception",
            "space_id": "Lobby Reception 1",
            "occurred_at": "8:40 AM today",
            "hazard_kind": "burning_smell",
            "smoke_present": True
        },
        "final_priority": "P1"
    },
    # Slot 12: english, rambling (unknown answer)
    {
        "id": "electrical_hazard_12",
        "incident_type": "electrical_hazard",
        "slot": 12,
        "lang_style": "english",
        "opening": "rambling",
        "turns": [
            {
                "role": "user",
                "text": "My laptop took forever to reboot this morning. There is an exposed wire in Stairwell 7 on floor 7 of Building N.",
                "sentences": [
                    {"text": "My laptop took forever to reboot this morning.", "relevant": False},
                    {"text": "There is an exposed wire in Stairwell 7 on floor 7 of Building N.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Is there any smoke?",
                "asks": ["smoke_present"]
            },
            {
                "role": "user",
                "text": "Not sure, I was walking down in a hurry.",
                "sentences": [
                    {"text": "Not sure, I was walking down in a hurry.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "When did you spot it?",
                "asks": ["occurred_at"]
            },
            {
                "role": "user",
                "text": "Around 11:45 AM today.",
                "sentences": [
                    {"text": "Around 11:45 AM today.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Understood, sending the electrical team right away.",
                "asks": []
            }
        ],
        "final_fields": {
            "building": "N",
            "floor": 7,
            "space_type": "stairwell",
            "space_id": "Stairwell 7",
            "occurred_at": "11:45 AM today",
            "hazard_kind": "exposed_wire",
            "smoke_present": "unknown"
        },
        "final_priority": "P1"
    },
    # Slot 13: hindi_translated, complete
    {
        "id": "electrical_hazard_13",
        "incident_type": "electrical_hazard",
        "slot": 13,
        "lang_style": "hindi_translated",
        "opening": "complete",
        "turns": [
            {
                "role": "user",
                "text": "In Building QRC on floor 4, Conference Room 4C has sparks flying from plug point, no smoke is there, noticed at 10:30 AM today morning.",
                "sentences": [
                    {"text": "In Building QRC on floor 4, Conference Room 4C has sparks flying from plug point, no smoke is there, noticed at 10:30 AM today morning.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Electricians have been alerted to inspect the outlet.",
                "asks": []
            }
        ],
        "final_fields": {
            "building": "QRC",
            "floor": 4,
            "space_type": "conference_room",
            "space_id": "Conference Room 4C",
            "occurred_at": "10:30 AM today",
            "hazard_kind": "sparks",
            "smoke_present": False
        },
        "final_priority": "P1"
    },
    # Slot 14: hindi_translated, missing
    {
        "id": "electrical_hazard_14",
        "incident_type": "electrical_hazard",
        "slot": 14,
        "lang_style": "hindi_translated",
        "opening": "missing",
        "turns": [
            {
                "role": "user",
                "text": "In Building N, floor 2, Pantry 2A inside, wire is open and hanging.",
                "sentences": [
                    {"text": "In Building N, floor 2, Pantry 2A inside, wire is open and hanging.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Is there any smoke?",
                "asks": ["smoke_present"]
            },
            {
                "role": "user",
                "text": "No smoke is coming, but wire is naked.",
                "sentences": [
                    {"text": "No smoke is coming, but wire is naked.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "At what time did you see this?",
                "asks": ["occurred_at"]
            },
            {
                "role": "user",
                "text": "Around 9:15 AM today morning.",
                "sentences": [
                    {"text": "Around 9:15 AM today morning.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Okay, the electrical team is heading there.",
                "asks": []
            }
        ],
        "final_fields": {
            "building": "N",
            "floor": 2,
            "space_type": "pantry",
            "space_id": "Pantry 2A",
            "occurred_at": "9:15 AM today",
            "hazard_kind": "exposed_wire",
            "smoke_present": False
        },
        "final_priority": "P1"
    },
    # Slot 15: hindi_translated, missing
    {
        "id": "electrical_hazard_15",
        "incident_type": "electrical_hazard",
        "slot": 15,
        "lang_style": "hindi_translated",
        "opening": "missing",
        "turns": [
            {
                "role": "user",
                "text": "Very hot smell of burning wire is coming in Server Room 5A, floor 5, Building N.",
                "sentences": [
                    {"text": "Very hot smell of burning wire is coming in Server Room 5A, floor 5, Building N.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Is there any smoke present?",
                "asks": ["smoke_present"]
            },
            {
                "role": "user",
                "text": "Yes, white smoke is visible near the UPS rack.",
                "sentences": [
                    {"text": "Yes, white smoke is visible near the UPS rack.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "When did you first notice this?",
                "asks": ["occurred_at"]
            },
            {
                "role": "user",
                "text": "Just now around 1:55 PM.",
                "sentences": [
                    {"text": "Just now around 1:55 PM.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Emergency electricians and safety responders are notified now.",
                "asks": []
            }
        ],
        "final_fields": {
            "building": "N",
            "floor": 5,
            "space_type": "server_or_idf_room",
            "space_id": "Server Room 5A",
            "occurred_at": "1:55 PM today",
            "hazard_kind": "burning_smell",
            "smoke_present": True
        },
        "final_priority": "P1"
    },
    # Slot 16: hindi_translated, rambling
    {
        "id": "electrical_hazard_16",
        "incident_type": "electrical_hazard",
        "slot": 16,
        "lang_style": "hindi_translated",
        "opening": "rambling",
        "turns": [
            {
                "role": "user",
                "text": "Today lunch was delayed very much. In Building QRC floor 6, one wall plug in Workstation Bay 6A is extremely hot.",
                "sentences": [
                    {"text": "Today lunch was delayed very much.", "relevant": False},
                    {"text": "In Building QRC floor 6, one wall plug in Workstation Bay 6A is extremely hot.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Is there any smoke coming?",
                "asks": ["smoke_present"]
            },
            {
                "role": "user",
                "text": "No smoke is there, but burning smell is not there, only plastic feels hot.",
                "sentences": [
                    {"text": "No smoke is there, but burning smell is not there, only plastic feels hot.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "When did you notice it?",
                "asks": ["occurred_at"]
            },
            {
                "role": "user",
                "text": "Around 2:40 PM afternoon.",
                "sentences": [
                    {"text": "Around 2:40 PM afternoon.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Thanks, electricians will inspect the socket right away.",
                "asks": []
            }
        ],
        "final_fields": {
            "building": "QRC",
            "floor": 6,
            "space_type": "workstation_bay",
            "space_id": "Workstation Bay 6A",
            "occurred_at": "2:40 PM today",
            "hazard_kind": "hot_outlet",
            "smoke_present": False
        },
        "final_priority": "P1"
    },
    # Slot 17: hinglish, missing
    {
        "id": "electrical_hazard_17",
        "incident_type": "electrical_hazard",
        "slot": 17,
        "lang_style": "hinglish",
        "opening": "missing",
        "turns": [
            {
                "role": "user",
                "text": "Building N ke floor 8 pe RF Lab 8 mein switchboard se sparks aa rahe hain.",
                "sentences": [
                    {"text": "Building N ke floor 8 pe RF Lab 8 mein switchboard se sparks aa rahe hain.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Is there any smoke present?",
                "asks": ["smoke_present"]
            },
            {
                "role": "user",
                "text": "Nahi, dhuan nahi hai, but sparks continuous hain.",
                "sentences": [
                    {"text": "Nahi, dhuan nahi hai, but sparks continuous hain.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Roughly when did this happen?",
                "asks": ["occurred_at"]
            },
            {
                "role": "user",
                "text": "Around 12:10 PM notice kiya.",
                "sentences": [
                    {"text": "Around 12:10 PM notice kiya.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Got it, electricians are on their way.",
                "asks": []
            }
        ],
        "final_fields": {
            "building": "N",
            "floor": 8,
            "space_type": "rnd_lab",
            "space_id": "RF Lab 8",
            "occurred_at": "12:10 PM today",
            "hazard_kind": "sparks",
            "smoke_present": False
        },
        "final_priority": "P1"
    },
    # Slot 18: hinglish, complete
    {
        "id": "electrical_hazard_18",
        "incident_type": "electrical_hazard",
        "slot": 18,
        "lang_style": "hinglish",
        "opening": "complete",
        "turns": [
            {
                "role": "user",
                "text": "Building QRC, floor 3 ke Corridor 3B mein switchboard se wire khula pada hai aur burning smell aa rahi hai without smoke, 11:20 AM pe dekha.",
                "sentences": [
                    {"text": "Building QRC, floor 3 ke Corridor 3B mein switchboard se wire khula pada hai aur burning smell aa rahi hai without smoke, 11:20 AM pe dekha.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Understood, emergency electricians are on the way.",
                "asks": []
            }
        ],
        "final_fields": {
            "building": "QRC",
            "floor": 3,
            "space_type": "corridor",
            "space_id": "Corridor 3B",
            "occurred_at": "11:20 AM today",
            "hazard_kind": "exposed_wire",
            "smoke_present": False
        },
        "final_priority": "P1"
    }
]

with open("data/electrical_hazard.jsonl", "w", encoding="utf-8") as f:
    for row in data:
        f.write(json.dumps(row) + "\n")

print("Generated electrical_hazard.jsonl successfully")
