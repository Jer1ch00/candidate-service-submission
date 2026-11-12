package com.recruiting.candidates.model;

/** How a multi-skill filter is applied. */
public enum SkillMatch {
    /** Candidate must have every requested skill ("must know Java AND Spring"). */
    ALL,
    /** Candidate must have at least one requested skill ("knows Kotlin OR Scala"). */
    ANY
}
