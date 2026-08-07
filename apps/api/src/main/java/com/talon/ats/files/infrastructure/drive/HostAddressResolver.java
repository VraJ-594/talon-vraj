package com.talon.ats.files.infrastructure.drive;

import java.io.IOException;
import java.net.InetAddress;
import java.util.List;

@FunctionalInterface
interface HostAddressResolver {

  List<InetAddress> resolve(String host) throws IOException;
}
