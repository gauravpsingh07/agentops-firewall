// ---------------------------------------------------------------------------
// Karma configuration for AgentOps Firewall.
//
// The default ChromeHeadless launcher refuses to start on Linux CI runners
// because Chrome's SUID sandbox requires the helper binary to be owned by
// root with mode 4755 — a constraint the GitHub-hosted runner image does
// not satisfy. Adding `--no-sandbox` is the standard fix; it loses one
// layer of process isolation but is the accepted tradeoff for headless
// browser test runs.
//
// `--disable-gpu` short-circuits a separate class of headless GPU init
// noise on Linux without changing what we test.
// ---------------------------------------------------------------------------

module.exports = function (config) {
  config.set({
    basePath: '',
    frameworks: ['jasmine', '@angular-devkit/build-angular'],
    plugins: [
      require('karma-jasmine'),
      require('karma-chrome-launcher'),
      require('karma-jasmine-html-reporter'),
      require('karma-coverage'),
      require('@angular-devkit/build-angular/plugins/karma')
    ],
    client: {
      jasmine: {
        // Stop the suite as soon as something fails — keeps CI logs tight.
        random: true,
        seed: '1234',
        stopOnFailure: false,
        failFast: false,
        timeoutInterval: 10000
      },
      clearContext: false
    },
    jasmineHtmlReporter: {
      suppressAll: true
    },
    coverageReporter: {
      dir: require('path').join(__dirname, './coverage/agentops-firewall-frontend'),
      subdir: '.',
      reporters: [
        { type: 'html' },
        { type: 'text-summary' }
      ]
    },
    reporters: ['progress', 'kjhtml'],
    port: 9876,
    colors: true,
    logLevel: config.LOG_INFO,
    autoWatch: false,
    customLaunchers: {
      ChromeHeadlessNoSandbox: {
        base: 'ChromeHeadless',
        flags: ['--no-sandbox', '--disable-gpu']
      }
    },
    browsers: ['ChromeHeadlessNoSandbox'],
    singleRun: true,
    restartOnFileChange: false
  });
};
