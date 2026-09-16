package com.apps.deen_sa.service;

import java.util.Map;

/** A deterministically qualified insight module ready for a story composer. */
public record StoryInsight(String key, Map<String, Object> facts) { }
