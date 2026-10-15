package com.neueda.leap.team.dto;

import java.util.List;

public record UserPageResponse(List<UserDto> content, int page, int size, long totalElements, int totalPages) {}
