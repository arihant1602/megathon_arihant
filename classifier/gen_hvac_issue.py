import json

data = [
    # Slot 1: english, complete
    {
        "id": "hvac_issue_01",
        "incident_type": "hvac_issue",
        "slot": 1,
        "lang_style": "english",
        "opening": "complete",
        "turns": [
            {
                "role": "user",
                "text": "The AC in Conference Room 3A on floor 3 of Building N is freezing cold across the whole room since 9:00 AM today.",
                "sentences": [
                    {"text": "The AC in Conference Room 3A on floor 3 of Building N is freezing cold across the whole room since 9:00 AM today.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Got it, I've asked the HVAC team to adjust the temperature.",
                "asks": []
            }
        ],
        "final_fields": {
            "building": "N",
            "floor": 3,
            "space_type": "conference_room",
            "space_id": "Conference Room 3A",
            "occurred_at": "9:00 AM today",
            "hvac_condition": "too_cold",
            "affected_scope": "room"
        },
        "final_priority": "P3"
    },
    # Slot 2: english, complete
    {
        "id": "hvac_issue_02",
        "incident_type": "hvac_issue",
        "slot": 2,
        "lang_style": "english",
        "opening": "complete",
        "turns": [
            {
                "role": "user",
                "text": "It is getting dangerously too hot in Server Room 2B on floor 2 of Building QRC, no airflow across the room since 1:15 PM today.",
                "sentences": [
                    {"text": "It is getting dangerously too hot in Server Room 2B on floor 2 of Building QRC, no airflow across the room since 1:15 PM today.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "High priority alert sent to the HVAC engineering team.",
                "asks": []
            }
        ],
        "final_fields": {
            "building": "QRC",
            "floor": 2,
            "space_type": "server_or_idf_room",
            "space_id": "Server Room 2B",
            "occurred_at": "1:15 PM today",
            "hvac_condition": "too_hot",
            "affected_scope": "room"
        },
        "final_priority": "P2"
    },
    # Slot 3: english, complete
    {
        "id": "hvac_issue_03",
        "incident_type": "hvac_issue",
        "slot": 3,
        "lang_style": "english",
        "opening": "complete",
        "turns": [
            {
                "role": "user",
                "text": "There is a very loud rattling noise from the AC duct affecting a single spot over Desk 8 in Workstation Bay 5B, floor 5, Building N since 10:30 AM today.",
                "sentences": [
                    {"text": "There is a very loud rattling noise from the AC duct affecting a single spot over Desk 8 in Workstation Bay 5B, floor 5, Building N since 10:30 AM today.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Thanks, an HVAC technician will check the duct.",
                "asks": []
            }
        ],
        "final_fields": {
            "building": "N",
            "floor": 5,
            "space_type": "workstation_bay",
            "space_id": "Workstation Bay 5B",
            "occurred_at": "10:30 AM today",
            "hvac_condition": "noise",
            "affected_scope": "single_spot"
        },
        "final_priority": "P3"
    },
    # Slot 4: english, missing
    {
        "id": "hvac_issue_04",
        "incident_type": "hvac_issue",
        "slot": 4,
        "lang_style": "english",
        "opening": "missing",
        "turns": [
            {
                "role": "user",
                "text": "The ventilation has stopped working in Cafeteria 1 on floor 1 of Building N.",
                "sentences": [
                    {"text": "The ventilation has stopped working in Cafeteria 1 on floor 1 of Building N.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Is it too hot, too cold, or no airflow?",
                "asks": ["hvac_condition"]
            },
            {
                "role": "user",
                "text": "There is completely no airflow, it feels very stuffy.",
                "sentences": [
                    {"text": "There is completely no airflow, it feels very stuffy.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Is it affecting the whole room or a spot?",
                "asks": ["affected_scope"]
            },
            {
                "role": "user",
                "text": "The entire room has lost airflow.",
                "sentences": [
                    {"text": "The entire room has lost airflow.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "When did you first notice this?",
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
                "text": "Got it, I've dispatched this to the HVAC team.",
                "asks": []
            }
        ],
        "final_fields": {
            "building": "N",
            "floor": 1,
            "space_type": "cafeteria",
            "space_id": "Cafeteria 1",
            "occurred_at": "11:45 AM today",
            "hvac_condition": "no_airflow",
            "affected_scope": "room"
        },
        "final_priority": "P3"
    },
    # Slot 5: english, missing (correction floor)
    {
        "id": "hvac_issue_05",
        "incident_type": "hvac_issue",
        "slot": 5,
        "lang_style": "english",
        "opening": "missing",
        "turns": [
            {
                "role": "user",
                "text": "The temperature in R&D Lab 4 is unbearable, Building QRC, floor 3.",
                "sentences": [
                    {"text": "The temperature in R&D Lab 4 is unbearable, Building QRC, floor 3.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Is it too hot or too cold?",
                "asks": ["hvac_condition"]
            },
            {
                "role": "user",
                "text": "Actually it is floor 4, not 3. It is way too hot in here.",
                "sentences": [
                    {"text": "Actually it is floor 4, not 3.", "relevant": True},
                    {"text": "It is way too hot in here.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Is it the whole room or one spot?",
                "asks": ["affected_scope"]
            },
            {
                "role": "user",
                "text": "The whole room is baking.",
                "sentences": [
                    {"text": "The whole room is baking.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Roughly when did this start?",
                "asks": ["occurred_at"]
            },
            {
                "role": "user",
                "text": "About 1:30 PM today.",
                "sentences": [
                    {"text": "About 1:30 PM today.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Understood, lab HVAC technicians have been alerted.",
                "asks": []
            }
        ],
        "final_fields": {
            "building": "QRC",
            "floor": 4,
            "space_type": "rnd_lab",
            "space_id": "R&D Lab 4",
            "occurred_at": "1:30 PM today",
            "hvac_condition": "too_hot",
            "affected_scope": "room"
        },
        "final_priority": "P2"
    },
    # Slot 6: english, missing (unknown scope)
    {
        "id": "hvac_issue_06",
        "incident_type": "hvac_issue",
        "slot": 6,
        "lang_style": "english",
        "opening": "missing",
        "turns": [
            {
                "role": "user",
                "text": "There is a strange musty odor coming from the air conditioning vents in Pantry 6A, floor 6, Building N.",
                "sentences": [
                    {"text": "There is a strange musty odor coming from the air conditioning vents in Pantry 6A, floor 6, Building N.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Is the smell across the room or one spot?",
                "asks": ["affected_scope"]
            },
            {
                "role": "user",
                "text": "Not sure how far it spreads, smelled it near the doorway.",
                "sentences": [
                    {"text": "Not sure how far it spreads, smelled it near the doorway.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "When did you first notice it?",
                "asks": ["occurred_at"]
            },
            {
                "role": "user",
                "text": "Around 9:45 AM today.",
                "sentences": [
                    {"text": "Around 9:45 AM today.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Okay, the HVAC maintenance crew will inspect the vents.",
                "asks": []
            }
        ],
        "final_fields": {
            "building": "N",
            "floor": 6,
            "space_type": "pantry",
            "space_id": "Pantry 6A",
            "occurred_at": "9:45 AM today",
            "hvac_condition": "odor",
            "affected_scope": "unknown"
        },
        "final_priority": "P3"
    },
    # Slot 7: english, missing
    {
        "id": "hvac_issue_07",
        "incident_type": "hvac_issue",
        "slot": 7,
        "lang_style": "english",
        "opening": "missing",
        "turns": [
            {
                "role": "user",
                "text": "The entire 8th floor of Building N is freezing cold.",
                "sentences": [
                    {"text": "The entire 8th floor of Building N is freezing cold.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Which bay or area are you sitting in?",
                "asks": ["space_id"]
            },
            {
                "role": "user",
                "text": "Workstation Bay 8C. The whole floor has freezing airflow.",
                "sentences": [
                    {"text": "Workstation Bay 8C.", "relevant": True},
                    {"text": "The whole floor has freezing airflow.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Roughly when did this start?",
                "asks": ["occurred_at"]
            },
            {
                "role": "user",
                "text": "Since 8:30 AM this morning.",
                "sentences": [
                    {"text": "Since 8:30 AM this morning.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Got it, HVAC techs have been asked to balance the zone.",
                "asks": []
            }
        ],
        "final_fields": {
            "building": "N",
            "floor": 8,
            "space_type": "workstation_bay",
            "space_id": "Workstation Bay 8C",
            "occurred_at": "8:30 AM today",
            "hvac_condition": "too_cold",
            "affected_scope": "floor"
        },
        "final_priority": "P3"
    },
    # Slot 8: english, missing
    {
        "id": "hvac_issue_08",
        "incident_type": "hvac_issue",
        "slot": 8,
        "lang_style": "english",
        "opening": "missing",
        "turns": [
            {
                "role": "user",
                "text": "Loud grinding noise is coming from the ceiling air conditioning unit.",
                "sentences": [
                    {"text": "Loud grinding noise is coming from the ceiling air conditioning unit.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Which building and floor is this on?",
                "asks": ["building", "floor"]
            },
            {
                "role": "user",
                "text": "Building QRC, floor 5, inside Conference Room 5A.",
                "sentences": [
                    {"text": "Building QRC, floor 5, inside Conference Room 5A.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Is the noise across the room or one spot?",
                "asks": ["affected_scope"]
            },
            {
                "role": "user",
                "text": "It echoes across the whole room.",
                "sentences": [
                    {"text": "It echoes across the whole room.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "When did you first hear it?",
                "asks": ["occurred_at"]
            },
            {
                "role": "user",
                "text": "About 2:45 PM today.",
                "sentences": [
                    {"text": "About 2:45 PM today.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Thanks, an HVAC technician will check the unit.",
                "asks": []
            }
        ],
        "final_fields": {
            "building": "QRC",
            "floor": 5,
            "space_type": "conference_room",
            "space_id": "Conference Room 5A",
            "occurred_at": "2:45 PM today",
            "hvac_condition": "noise",
            "affected_scope": "room"
        },
        "final_priority": "P3"
    },
    # Slot 9: english, missing
    {
        "id": "hvac_issue_09",
        "incident_type": "hvac_issue",
        "slot": 9,
        "lang_style": "english",
        "opening": "missing",
        "turns": [
            {
                "role": "user",
                "text": "The temperature is way too warm in IDF Room 10A on floor 10 of Building N.",
                "sentences": [
                    {"text": "The temperature is way too warm in IDF Room 10A on floor 10 of Building N.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Is it the whole room or single spot?",
                "asks": ["affected_scope"]
            },
            {
                "role": "user",
                "text": "The whole room is overheating.",
                "sentences": [
                    {"text": "The whole room is overheating.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Roughly when did you notice the temperature rise?",
                "asks": ["occurred_at"]
            },
            {
                "role": "user",
                "text": "Around 12:00 PM today.",
                "sentences": [
                    {"text": "Around 12:00 PM today.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Critical HVAC response dispatched for IDF cooling.",
                "asks": []
            }
        ],
        "final_fields": {
            "building": "N",
            "floor": 10,
            "space_type": "server_or_idf_room",
            "space_id": "IDF Room 10A",
            "occurred_at": "12:00 PM today",
            "hvac_condition": "too_hot",
            "affected_scope": "room"
        },
        "final_priority": "P2"
    },
    # Slot 10: english, rambling
    {
        "id": "hvac_issue_10",
        "incident_type": "hvac_issue",
        "slot": 10,
        "lang_style": "english",
        "opening": "rambling",
        "turns": [
            {
                "role": "user",
                "text": "I had to skip breakfast this morning because of morning meetings. Also, Workstation Bay 12A on floor 12 of Building N is freezing cold.",
                "sentences": [
                    {"text": "I had to skip breakfast this morning because of morning meetings.", "relevant": False},
                    {"text": "Also, Workstation Bay 12A on floor 12 of Building N is freezing cold.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Is it affecting a single spot or the room?",
                "asks": ["affected_scope"]
            },
            {
                "role": "user",
                "text": "I need to grab a sandwich soon. It affects only a single spot right over my desk.",
                "sentences": [
                    {"text": "I need to grab a sandwich soon.", "relevant": False},
                    {"text": "It affects only a single spot right over my desk.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "When did you first feel the cold airflow?",
                "asks": ["occurred_at"]
            },
            {
                "role": "user",
                "text": "Around 10:15 AM today.",
                "sentences": [
                    {"text": "Around 10:15 AM today.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Understood, an HVAC technician will check the vent.",
                "asks": []
            }
        ],
        "final_fields": {
            "building": "N",
            "floor": 12,
            "space_type": "workstation_bay",
            "space_id": "Workstation Bay 12A",
            "occurred_at": "10:15 AM today",
            "hvac_condition": "too_cold",
            "affected_scope": "single_spot"
        },
        "final_priority": "P3"
    },
    # Slot 11: english, rambling (correction on space_id)
    {
        "id": "hvac_issue_11",
        "incident_type": "hvac_issue",
        "slot": 11,
        "lang_style": "english",
        "opening": "rambling",
        "turns": [
            {
                "role": "user",
                "text": "The metro was completely jam-packed today. The AC has zero airflow in Conference Room 2A on floor 2 of Building QRC.",
                "sentences": [
                    {"text": "The metro was completely jam-packed today.", "relevant": False},
                    {"text": "The AC has zero airflow in Conference Room 2A on floor 2 of Building QRC.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Is it affecting the whole room or one spot?",
                "asks": ["affected_scope"]
            },
            {
                "role": "user",
                "text": "Actually it is Conference Room 2C, not 2A. The whole room has no airflow.",
                "sentences": [
                    {"text": "Actually it is Conference Room 2C, not 2A.", "relevant": True},
                    {"text": "The whole room has no airflow.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Roughly when did you notice this?",
                "asks": ["occurred_at"]
            },
            {
                "role": "user",
                "text": "About 1:40 PM today.",
                "sentences": [
                    {"text": "About 1:40 PM today.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Got it, I've informed the HVAC technicians.",
                "asks": []
            }
        ],
        "final_fields": {
            "building": "QRC",
            "floor": 2,
            "space_type": "conference_room",
            "space_id": "Conference Room 2C",
            "occurred_at": "1:40 PM today",
            "hvac_condition": "no_airflow",
            "affected_scope": "room"
        },
        "final_priority": "P3"
    },
    # Slot 12: english, rambling (unknown condition)
    {
        "id": "hvac_issue_12",
        "incident_type": "hvac_issue",
        "slot": 12,
        "lang_style": "english",
        "opening": "rambling",
        "turns": [
            {
                "role": "user",
                "text": "Friday afternoon can't come soon enough. The AC vents are acting very weird in Corridor 7A on floor 7 of Building N.",
                "sentences": [
                    {"text": "Friday afternoon can't come soon enough.", "relevant": False},
                    {"text": "The AC vents are acting very weird in Corridor 7A on floor 7 of Building N.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Is it too hot, too cold, noise, or smell?",
                "asks": ["hvac_condition"]
            },
            {
                "role": "user",
                "text": "I am not sure, it's just really uncomfortable and whistling.",
                "sentences": [
                    {"text": "I am not sure, it's just really uncomfortable and whistling.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Is it affecting the whole floor or one spot?",
                "asks": ["affected_scope"]
            },
            {
                "role": "user",
                "text": "Just a single spot near the elevator.",
                "sentences": [
                    {"text": "Just a single spot near the elevator.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "When did you notice it?",
                "asks": ["occurred_at"]
            },
            {
                "role": "user",
                "text": "Around 3:20 PM today.",
                "sentences": [
                    {"text": "Around 3:20 PM today.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Thanks, our HVAC team will check the vent.",
                "asks": []
            }
        ],
        "final_fields": {
            "building": "N",
            "floor": 7,
            "space_type": "corridor",
            "space_id": "Corridor 7A",
            "occurred_at": "3:20 PM today",
            "hvac_condition": "unknown",
            "affected_scope": "single_spot"
        },
        "final_priority": "P3"
    },
    # Slot 13: hindi_translated, complete
    {
        "id": "hvac_issue_13",
        "incident_type": "hvac_issue",
        "slot": 13,
        "lang_style": "hindi_translated",
        "opening": "complete",
        "turns": [
            {
                "role": "user",
                "text": "In Building QRC on floor 1, Cafeteria 1 is very too hot across whole room since 12:30 PM today afternoon.",
                "sentences": [
                    {"text": "In Building QRC on floor 1, Cafeteria 1 is very too hot across whole room since 12:30 PM today afternoon.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Understood, HVAC technician is sent to adjust the cooling.",
                "asks": []
            }
        ],
        "final_fields": {
            "building": "QRC",
            "floor": 1,
            "space_type": "cafeteria",
            "space_id": "Cafeteria 1",
            "occurred_at": "12:30 PM today",
            "hvac_condition": "too_hot",
            "affected_scope": "room"
        },
        "final_priority": "P3"
    },
    # Slot 14: hindi_translated, missing
    {
        "id": "hvac_issue_14",
        "incident_type": "hvac_issue",
        "slot": 14,
        "lang_style": "hindi_translated",
        "opening": "missing",
        "turns": [
            {
                "role": "user",
                "text": "In Building N, floor 4, Workstation Bay 4B, air conditioning is making very loud whistling noise.",
                "sentences": [
                    {"text": "In Building N, floor 4, Workstation Bay 4B, air conditioning is making very loud whistling noise.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Is the noise in a single spot or whole room?",
                "asks": ["affected_scope"]
            },
            {
                "role": "user",
                "text": "It is in single spot over my desk.",
                "sentences": [
                    {"text": "It is in single spot over my desk.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "When did you first hear this?",
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
                "text": "Got it, the HVAC team will inspect the vent.",
                "asks": []
            }
        ],
        "final_fields": {
            "building": "N",
            "floor": 4,
            "space_type": "workstation_bay",
            "space_id": "Workstation Bay 4B",
            "occurred_at": "9:15 AM today",
            "hvac_condition": "noise",
            "affected_scope": "single_spot"
        },
        "final_priority": "P3"
    },
    # Slot 15: hindi_translated, missing
    {
        "id": "hvac_issue_15",
        "incident_type": "hvac_issue",
        "slot": 15,
        "lang_style": "hindi_translated",
        "opening": "missing",
        "turns": [
            {
                "role": "user",
                "text": "In Building N, floor 6, R&D Lab 6 is heating up too much.",
                "sentences": [
                    {"text": "In Building N, floor 6, R&D Lab 6 is heating up too much.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Is the whole room too hot?",
                "asks": ["affected_scope"]
            },
            {
                "role": "user",
                "text": "Yes, whole room is completely too hot.",
                "sentences": [
                    {"text": "Yes, whole room is completely too hot.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "When did you first notice this?",
                "asks": ["occurred_at"]
            },
            {
                "role": "user",
                "text": "About 2:10 PM today afternoon.",
                "sentences": [
                    {"text": "About 2:10 PM today afternoon.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Alerting lab HVAC engineers right now.",
                "asks": []
            }
        ],
        "final_fields": {
            "building": "N",
            "floor": 6,
            "space_type": "rnd_lab",
            "space_id": "R&D Lab 6",
            "occurred_at": "2:10 PM today",
            "hvac_condition": "too_hot",
            "affected_scope": "room"
        },
        "final_priority": "P2"
    },
    # Slot 16: hindi_translated, rambling
    {
        "id": "hvac_issue_16",
        "incident_type": "hvac_issue",
        "slot": 16,
        "lang_style": "hindi_translated",
        "opening": "rambling",
        "turns": [
            {
                "role": "user",
                "text": "Tea in cafeteria was too sweet today. In Building QRC, floor 3, Conference Room 3B has foul odor from AC.",
                "sentences": [
                    {"text": "Tea in cafeteria was too sweet today.", "relevant": False},
                    {"text": "In Building QRC, floor 3, Conference Room 3B has foul odor from AC.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Is the odor across the whole room?",
                "asks": ["affected_scope"]
            },
            {
                "role": "user",
                "text": "Yes, full room has this bad smell.",
                "sentences": [
                    {"text": "Yes, full room has this bad smell.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "When did you first notice the smell?",
                "asks": ["occurred_at"]
            },
            {
                "role": "user",
                "text": "Around 11:20 AM today.",
                "sentences": [
                    {"text": "Around 11:20 AM today.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Okay, HVAC technicians will inspect the filters.",
                "asks": []
            }
        ],
        "final_fields": {
            "building": "QRC",
            "floor": 3,
            "space_type": "conference_room",
            "space_id": "Conference Room 3B",
            "occurred_at": "11:20 AM today",
            "hvac_condition": "odor",
            "affected_scope": "room"
        },
        "final_priority": "P3"
    },
    # Slot 17: hinglish, missing
    {
        "id": "hvac_issue_17",
        "incident_type": "hvac_issue",
        "slot": 17,
        "lang_style": "hinglish",
        "opening": "missing",
        "turns": [
            {
                "role": "user",
                "text": "Building N ke floor 9 pe Workstation Bay 9A mein AC bilkul kaam nahi kar raha, no airflow.",
                "sentences": [
                    {"text": "Building N ke floor 9 pe Workstation Bay 9A mein AC bilkul kaam nahi kar raha, no airflow.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Is it a single spot or whole room?",
                "asks": ["affected_scope"]
            },
            {
                "role": "user",
                "text": "Pura room stuffy ho gaya hai.",
                "sentences": [
                    {"text": "Pura room stuffy ho gaya hai.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Roughly when did this happen?",
                "asks": ["occurred_at"]
            },
            {
                "role": "user",
                "text": "Around 1:00 PM notice kiya.",
                "sentences": [
                    {"text": "Around 1:00 PM notice kiya.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Got it, I've assigned an HVAC technician.",
                "asks": []
            }
        ],
        "final_fields": {
            "building": "N",
            "floor": 9,
            "space_type": "workstation_bay",
            "space_id": "Workstation Bay 9A",
            "occurred_at": "1:00 PM today",
            "hvac_condition": "no_airflow",
            "affected_scope": "room"
        },
        "final_priority": "P3"
    },
    # Slot 18: hinglish, complete
    {
        "id": "hvac_issue_18",
        "incident_type": "hvac_issue",
        "slot": 18,
        "lang_style": "hinglish",
        "opening": "complete",
        "turns": [
            {
                "role": "user",
                "text": "Building QRC, floor 6 ke Server Room 6A mein temperature bohot high ho gaya hai too hot pura room 2:15 PM se.",
                "sentences": [
                    {"text": "Building QRC, floor 6 ke Server Room 6A mein temperature bohot high ho gaya hai too hot pura room 2:15 PM se.", "relevant": True}
                ]
            },
            {
                "role": "assistant",
                "text": "Immediate alert sent to the HVAC team.",
                "asks": []
            }
        ],
        "final_fields": {
            "building": "QRC",
            "floor": 6,
            "space_type": "server_or_idf_room",
            "space_id": "Server Room 6A",
            "occurred_at": "2:15 PM today",
            "hvac_condition": "too_hot",
            "affected_scope": "room"
        },
        "final_priority": "P2"
    }
]

with open("data/hvac_issue.jsonl", "w", encoding="utf-8") as f:
    for row in data:
        f.write(json.dumps(row) + "\n")

print("Generated hvac_issue.jsonl successfully")
