package com.apps.deen_sa.finance.presentation;

import java.math.BigDecimal;

public record FlowPoint(String account, String category, BigDecimal amount) { }
