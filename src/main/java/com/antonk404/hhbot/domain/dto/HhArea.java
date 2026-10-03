package com.antonk404.hhbot.domain.dto;

/** @param parent регион или страна, куда входит город; пусто у стран */
public record HhArea(int id, String name, String parent) {
}
