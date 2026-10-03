package com.antonk404.hhbot.domain.dto;

import java.util.List;

/** @param ownerName имя из профиля, может быть пустым */
public record HhProfile(String ownerName, List<HhResume> resumes) {
}
