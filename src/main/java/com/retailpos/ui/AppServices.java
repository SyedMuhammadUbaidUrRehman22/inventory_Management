package com.retailpos.ui;

import com.retailpos.service.AccessControl;
import com.retailpos.service.ProductCatalog;

/** The services the screens use; created once in {@code App.main}. Later phases add their services here. */
public record AppServices(AccessControl accessControl, ProductCatalog productCatalog) {
}
