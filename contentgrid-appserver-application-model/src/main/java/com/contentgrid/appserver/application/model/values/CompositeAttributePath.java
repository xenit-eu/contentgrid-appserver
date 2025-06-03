package com.contentgrid.appserver.application.model.values;

import lombok.Value;

@Value
public class CompositeAttributePath implements AttributePath {
    AttributeName first;
    AttributePath rest;
}
