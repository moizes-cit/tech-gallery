module.exports = function ($rootScope) {

  /**
   * Object context
   * @type {Object}
   */
  var context = this;

  /**
   * Application loading state
   * @type {Boolean}
   */
  this.loading = true;

  /**
   * Application name
   * @type {String}
   */
  this.appName = 'Tech Gallery';

  /**
   * Update the page <title>. Add the appName at the end.
   *
   * @param {String} title The new page title
   */
  this.setPageTitle = function (title) {
    if (title && title.length) {
      $rootScope.pageTitle = title + ' - ' + context.appName;
    }
  }

  this.setLoading = function (state) {
    context.loading = !!state;
    console.log('set loading state to:', context.loading, !!state, state);
    // $rootScope.$apply();
  }

  this.setPageTitle('Index');
}