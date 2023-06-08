module SusInjection.main {
    requires java.rmi;
    requires java.instrument;

    requires jdk.attach;
    requires jdk.unsupported;

    requires org.jetbrains.annotations;

    exports me.lollopollqo.sus.injection.util;
    exports me.lollopollqo.sus.injection.rmi;
    exports me.lollopollqo.sus.injection.agent;
}