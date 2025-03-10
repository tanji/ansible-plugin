package com.rundeck.plugins.ansible.plugin;

import com.dtolabs.rundeck.core.execution.ExecutionContext;
import com.dtolabs.rundeck.core.execution.proxy.ProxySecretBundleCreator;
import com.dtolabs.rundeck.core.execution.proxy.SecretBundle;
import com.rundeck.plugins.ansible.ansible.AnsibleDescribable;
import com.rundeck.plugins.ansible.ansible.AnsibleException;
import com.rundeck.plugins.ansible.ansible.AnsibleRunner;
import com.rundeck.plugins.ansible.ansible.AnsibleRunnerBuilder;
import com.dtolabs.rundeck.core.execution.workflow.steps.StepException;
import com.dtolabs.rundeck.core.plugins.Plugin;
import com.dtolabs.rundeck.core.plugins.configuration.ConfigurationException;
import com.dtolabs.rundeck.core.plugins.configuration.Description;
import com.dtolabs.rundeck.plugins.ServiceNameConstants;
import com.dtolabs.rundeck.plugins.step.PluginStepContext;
import com.dtolabs.rundeck.plugins.step.StepPlugin;
import com.dtolabs.rundeck.plugins.util.DescriptionBuilder;
import com.rundeck.plugins.ansible.util.AnsibleUtil;

import java.util.List;
import java.util.Map;

@Plugin(name = AnsibleModuleWorkflowStep.SERVICE_PROVIDER_NAME, service = ServiceNameConstants.WorkflowStep)
public class AnsibleModuleWorkflowStep implements StepPlugin, AnsibleDescribable, ProxySecretBundleCreator {

    public static final String SERVICE_PROVIDER_NAME = "com.batix.rundeck.plugins.AnsibleModuleWorkflowStep";

    public static Description DESC = null;

    static {
        DescriptionBuilder builder = DescriptionBuilder.builder();
        builder.name(SERVICE_PROVIDER_NAME);
        builder.title("Ansible Module");
        builder.description("Runs an Ansible Module on selected nodes.");

        builder.property(BINARIES_DIR_PATH_PROP);
        builder.property(BASE_DIR_PROP);
        builder.property(INVENTORY_INLINE_PROP);
        builder.property(MODULE_PROP);
        builder.property(MODULE_ARGS_PROP);
        builder.property(SSH_AUTH_TYPE_PROP);
        builder.property(SSH_USER_PROP);
        builder.property(SSH_PASSWORD_STORAGE_PROP);
        builder.property(SSH_KEY_FILE_PROP);
        builder.property(SSH_KEY_STORAGE_PROP);
        builder.property(SSH_TIMEOUT_PROP);
        builder.property(SSH_USE_AGENT);
        builder.property(SSH_PASSPHRASE);
        builder.property(SSH_PASSPHRASE_OPTION);
        builder.property(BECOME_PROP);
        builder.property(BECOME_AUTH_TYPE_PROP);
        builder.property(BECOME_USER_PROP);
        builder.property(BECOME_PASSWORD_STORAGE_PROP);

        DESC = builder.build();
    }

    @Override
    public void executeStep(PluginStepContext context, Map<String, Object> configuration) throws StepException {

        AnsibleRunner runner = null;

        // set targets
        StringBuilder nodes = new StringBuilder();
        for (String node : context.getNodes().getNodeNames()) {
            nodes.append(node);
            nodes.append(",");
        }
        String limit = nodes.length() > 0 ? nodes.substring(0, nodes.length() - 1) : "";
        if (limit != "") {
            configuration.put(AnsibleDescribable.ANSIBLE_LIMIT, limit);
        }

        // set log level
        if (context.getDataContext().get("job").get("loglevel").equals("DEBUG")) {
            configuration.put(AnsibleDescribable.ANSIBLE_DEBUG, "True");
        } else {
            configuration.put(AnsibleDescribable.ANSIBLE_DEBUG, "False");
        }

        AnsibleRunnerBuilder builder = new AnsibleRunnerBuilder(context.getExecutionContext(), context.getFramework(), context.getNodes(), configuration);

        try {
            runner = builder.buildAnsibleRunner();
        } catch (ConfigurationException e) {
            throw new StepException("Error configuring Ansible runner: " + e.getMessage(), e, AnsibleException.AnsibleFailureReason.ParseArgumentsError);
        }

        // ansible runner will take care of handling exceptions, here handle only jobs specific stuff
        try {
            runner.run();
        } catch (AnsibleException e) {
            throw new StepException(e.getMessage(), e, e.getFailureReason());
        } catch (Exception e) {
            throw new StepException(e.getMessage(), e, AnsibleException.AnsibleFailureReason.AnsibleError);
        }

        builder.cleanupTempFiles();
    }

    @Override
    public Description getDescription() {
        return DESC;
    }

    @Override
    public SecretBundle prepareSecretBundleWorkflowStep(ExecutionContext context, Map<String, Object> configuration) {
        AnsibleRunnerBuilder builder = new AnsibleRunnerBuilder(context, context.getFramework(), context.getNodes(), configuration);
        return AnsibleUtil.createBundle(builder);
    }

    @Override
    public List<String> listSecretsPathWorkflowStep(ExecutionContext context, Map<String, Object> configuration) {
        AnsibleRunnerBuilder builder = new AnsibleRunnerBuilder(context, context.getFramework(), context.getNodes(), configuration);
        return AnsibleUtil.getSecretsPath(builder);
    }
}
