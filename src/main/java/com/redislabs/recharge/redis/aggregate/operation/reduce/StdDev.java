package com.redislabs.recharge.redis.aggregate.operation.reduce;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class StdDev extends AbstractReducer {

	private String property;

}