module SusInjection.main {
    requires java.rmi;
    requires java.instrument;

    requires jdk.attach;
    // For some reason IDEA doesn't see that this is actually necessary
    //noinspection Java9RedundantRequiresStatement
    requires jdk.unsupported;

    requires org.jetbrains.annotations;

    exports me.lollopollqo.sus.injection.util;
    exports me.lollopollqo.sus.injection.rmi;
    exports me.lollopollqo.sus.injection.agent;
}