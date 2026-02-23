package com.tienlen.be.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class Card {
    private int rank;   // 3 → 15 (2 = 15)
    private int suit; // 1,2,3,4
}
