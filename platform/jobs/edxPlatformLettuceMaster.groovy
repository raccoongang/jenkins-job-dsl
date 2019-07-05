package platform

import org.yaml.snakeyaml.Yaml
import static org.edx.jenkins.dsl.JenkinsPublicConstants.GENERAL_PRIVATE_JOB_SECURITY
import static org.edx.jenkins.dsl.JenkinsPublicConstants.JENKINS_PUBLIC_LOG_ROTATOR
import static org.edx.jenkins.dsl.JenkinsPublicConstants.GENERAL_SLACK_STATUS
import static org.edx.jenkins.dsl.JenkinsPublicConstants.JENKINS_PUBLIC_BASE_URL
import static org.edx.jenkins.dsl.JenkinsPublicConstants.JENKINS_PUBLIC_GITHUB_BASEURL
import static org.edx.jenkins.dsl.JenkinsPublicConstants.JENKINS_PUBLIC_JUNIT_REPORTS
import static org.edx.jenkins.dsl.JenkinsPublicConstants.JENKINS_PUBLIC_GITHUB_STATUS_SUCCESS
import static org.edx.jenkins.dsl.JenkinsPublicConstants.JENKINS_PUBLIC_GITHUB_STATUS_UNSTABLE_OR_WORSE


/* stdout logger */
/* use this instead of println, because you can pass it into closures or other scripts. */
Map config = [:]
Binding bindings = getBinding()
config.putAll(bindings.getVariables())
PrintStream out = config['out']

/* Get external variables */
repo_name = System.getenv('LETTUCE_MASTER_REPO_NAME')
ficus_branch_name = System.getenv('FICUS_BRANCH_NAME')
ginkgo_branch_name = System.getenv('GINKGO_BRANCH_NAME')
hawthorn_branch_name = System.getenv('HAWTHORN_BRANCH_NAME')

ficus_test_branch_name = System.getenv('FICUS_TEST_BRANCH_NAME')
ginkgo_test_branch_name = System.getenv('GINKGO_TEST_BRANCH_NAME')
hawthorn_test_branch_name = System.getenv('HAWTHORN_TEST_BRANCH_NAME')

// This script generates a lot of jobs. Here is the breakdown of the configuration options:
// Map exampleConfig = [
//     open: true/false if this job should be 'open' (use the default security scheme or not)
//     jobName: name of the job
//     subsetjob: name of subset job run by this job (shard jobs)
//     repoName: name of the github repo containing the edx-platform you want to test
//     workerLabel: label of the worker to run the subset jobs on
//     context: Github context used to report test status
//     defaultTestengBranch: default branch of the testeng-ci repo for this job
//     refSpec: refspec for branches to build
//     defaultBranch: branch to build
// ]

Map publicJobConfig = [
    open: true,
    jobName: 'edx-platform-lettuce-master',
    subsetJob: 'edx-platform-test-subset',
    repoName: repo_name,
    workerLabel: 'jenkins-worker',
    context: 'jenkins/lettuce',
    defaultTestengBranch: 'master',
    refSpec : '+refs/heads/master:refs/remotes/origin/master',
    defaultBranch : 'master'
]

Map hawthornJobConfig = [
    open: true,
    jobName: 'hawthorn-lettuce-master',
    subsetJob: 'edx-platform-test-subset',
    repoName: repo_name,
    workerLabel: 'hawthorn-jenkins-worker',
    context: 'jenkins/hawthorn/lettuce',
    defaultTestengBranch : 'refs/heads/' + hawthorn_test_branch_name,
    refSpec : '+refs/heads/' + hawthorn_branch_name + ':refs/remotes/origin/' + hawthorn_branch_name,
    defaultBranch : 'refs/heads/' + hawthorn_branch_name
]

Map ginkgoJobConfig = [
    open: true,
    jobName: 'ginkgo-lettuce-master',
    subsetJob: 'edx-platform-test-subset',
    repoName: repo_name,
    workerLabel: 'ginkgo-jenkins-worker',
    context: 'jenkins/ginkgo/lettuce',
    defaultTestengBranch : 'refs/heads/' + ginkgo_test_branch_name,
    refSpec : '+refs/heads/' + ginkgo_branch_name + ':refs/remotes/origin/' + ginkgo_branch_name,
    defaultBranch : 'refs/heads/' + ginkgo_branch_name
]

Map ficusJobConfig = [
    open: true,
    jobName: 'ficus-lettuce-master',
    subsetJob: 'edx-platform-test-subset',
    repoName: repo_name,
    workerLabel: 'ficus-jenkins-worker',
    context: 'jenkins/ficus/lettuce',
    defaultTestengBranch : 'refs/heads/' + ficus_test_branch_name,
    refSpec : '+refs/heads/' + ficus_branch_name + ':refs/remotes/origin/' + ficus_branch_name,
    defaultBranch : 'refs/heads/' + ficus_branch_name
]

List jobConfigs = [
    publicJobConfig,
    hawthornJobConfig,
    ginkgoJobConfig,
    ficusJobConfig
]

/* Iterate over the job configurations */
jobConfigs.each { jobConfig ->

    buildFlowJob(jobConfig.jobName) {

        /* For non-open jobs, enable project based security */
        if (!jobConfig.open.toBoolean()) {
            authorization GENERAL_PRIVATE_JOB_SECURITY()
        }
        properties {
            githubProjectUrl("https://github.com/raccoongang/${jobConfig.repoName}/")
        }
        logRotator JENKINS_PUBLIC_LOG_ROTATOR(7)
	disabled()
        concurrentBuild()
        label('flow-worker-lettuce')
        checkoutRetryCount(5)
        environmentVariables {
            env('SUBSET_JOB', jobConfig.subsetJob)
            env('REPO_NAME', jobConfig.repoName)
        }
        parameters {
            stringParam('WORKER_LABEL', jobConfig.workerLabel, 'Jenkins worker for running the test subset jobs')
        }
        multiscm {
            git {
                remote {
                    url("git@github.com:raccoongang/${jobConfig.repoName}.git")
                    refspec(jobConfig.refSpec)
                    credentials('jenkins-worker')
                }
                branch(jobConfig.defaultBranch)
                browser()
                extensions {
                    cloneOptions {
                        // Use a reference clone for quicker clones. This is configured on jenkins workers via
                        // (https://github.com/edx/configuration/blob/master/playbooks/roles/test_build_server/tasks/main.yml#L26)
                        reference("\$HOME/edx-platform-clone")
                        timeout(10)
                    }
                    cleanBeforeCheckout()
                    relativeTargetDirectory(jobConfig.repoName)
                }
            }
            git {
                remote {
                    url('https://github.com/edx/testeng-ci.git')
                }
                branch(jobConfig.defaultTestengBranch)
                browser()
                extensions {
                    cleanBeforeCheckout()
                    relativeTargetDirectory('testeng-ci')
                }
            }
        }
        triggers { githubPush() }
        wrappers {
            timestamps()
        }

        Map <String, String> predefinedPropsMap  = [:]
        predefinedPropsMap.put('GIT_SHA', '${GIT_COMMIT}')
        predefinedPropsMap.put('GITHUB_ORG', 'raccoongang')
        predefinedPropsMap.put('CONTEXT', jobConfig.context)

        dslFile('testeng-ci/jenkins/flow/master/edx-platform-lettuce-master.groovy')
        publishers {
           archiveJunit(JENKINS_PUBLIC_JUNIT_REPORTS)
           predefinedPropsMap.put('GITHUB_REPO', jobConfig.repoName)
           predefinedPropsMap.put('TARGET_URL', JENKINS_PUBLIC_BASE_URL +
                                  'job/' + jobConfig.jobName + '/${BUILD_NUMBER}/')
           downstreamParameterized JENKINS_PUBLIC_GITHUB_STATUS_SUCCESS.call(predefinedPropsMap)
           downstreamParameterized JENKINS_PUBLIC_GITHUB_STATUS_UNSTABLE_OR_WORSE.call(predefinedPropsMap)
           mailer('testeng@edx.org')
           configure GENERAL_SLACK_STATUS()
       }
    }
}
