package com.dokpamine.domain.user.dto.request;

import jakarta.validation.constraints.*;

public record UpdateMeRequest(@Size(min = 1, max = 30) String nickname, Boolean rankingOptIn) {}
