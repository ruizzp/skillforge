package com.skillforge.hub.domain;

public enum SkillValidation {
    DECLARED,   // autodeclarado no manifest
    PROVEN,     // validado por Master/Archmage via label na issue
    UNKNOWN
}