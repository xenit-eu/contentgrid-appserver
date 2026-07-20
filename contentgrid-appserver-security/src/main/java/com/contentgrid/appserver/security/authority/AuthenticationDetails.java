package com.contentgrid.appserver.security.authority;


public interface AuthenticationDetails {

    Actor getPrincipal();

    Actor getActor();
}
