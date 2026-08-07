package com.talon.ats.identity.application;

public record AuthenticateCommand(String email, String password) {}
