package com.contentgrid.appserver.autoconfigure.security.authority;

public interface AuthenticationDetails {

    Actor principal();

    Actor actor();
}
