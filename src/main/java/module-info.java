module me.lollopollqo.sus.injection.main {
    requires java.rmi;
    requires java.instrument;

    requires jdk.attach;

    requires org.jetbrains.annotations;
    requires org.objectweb.asm;

    exports me.lollopollqo.sus.injection.util;
    exports me.lollopollqo.sus.injection.rmi;
    exports me.lollopollqo.sus.injection.agent;
}