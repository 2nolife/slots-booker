app.controller('manageUsersController', function($scope, manageUsersService, apiUsersService, notifyService, $timeout, modalDialogService, config) {

  function EditedUser(/*User*/ user) {

    var _this = this
    var _user = $.extend(true, {}, user, { roles: [] })

    this.id = _user.id
    this.username = _user.username
    this.email = _user.email
    this.metadata = JSON.stringify(_user.metadata, null, 4)
    this.attributes = JSON.stringify(_user.attributes, null, 4)
    this.roles = {}
    this.password = null

    config.all_user_roles.forEach(function(role) {
      _this.roles[role] = _user.roles.indexOf(role) != -1
    })

    function selectedRoles() {
      return $.grep(Object.keys(_this.roles), function(role) { return _this.roles[role] })
    }

    this.toApiProfileEntity = function() {
      var entity = {
        username: _this.username,
        email: _this.email,
        roles: selectedRoles()
      }

      if (_this.password) entity.password = _this.password
      if (_this.metadata) entity.metadata = JSON.parse(_this.metadata)
      if (_this.attributes) entity.attributes = JSON.parse(_this.attributes)

      return entity
    }

  }

  $scope.loadUsers = function(/*bool*/ force) {
    $scope.loadUsersSpin = true
    manageUsersService.loadUsers(function(/*[User]*/ users) {

      $scope.managedUsers = users

      $timeout(function() { $scope.loadUsersSpin = false }, 1000)

    }, force)
  }

  var selectUserByIdToEdit = function(/*str*/ id) {
    $scope.editedUser = null

    var users = $.grep($scope.managedUsers, function(user) { return user.id == id })
    if (users.length > 0) $scope.editedUser = new EditedUser(users[0])

    return $scope.editedUser
  }

  $scope.showEditUserFragment = function(/*str*/ id) {
    if (selectUserByIdToEdit(id)) {
      $scope.dialogOptions = {
        title: 'Edit User Details',
        onSubmit: submitEditedUser,
        onDelete: deleteEditedUser,
        onSignOut: signOutEditedUser
      }
      editUserDialog.show()
    }
  }

  $scope.showAddUserFragment = function() {
    $scope.editedUser = new EditedUser()
    $scope.dialogOptions = {
      title: 'Add a New User',
      onSubmit: submitNewUser
    }
    editUserDialog.show()
  }

  var submitEditedUser = function() {
    apiUsersService.patchUser($scope.editedUser.id, $scope.editedUser.toApiProfileEntity(), function() {
      notifyService.notify('User details updated', 'success')
      $scope.loadUsers(true);
    });
  }

  var submitNewUser = function() {
    apiUsersService.addUser($scope.editedUser.toApiProfileEntity(), function() {
      notifyService.notify('New user added', 'success')
      $scope.loadUsers(true);
    });
  }

  var signOutEditedUser = function() {
    apiUsersService.signOutUser($scope.editedUser.id, function() {
      notifyService.notify('User signed out', 'success')
    });
  }

  var deleteEditedUser = function() {
    apiUsersService.deleteUser($scope.editedUser.id, function() {
      notifyService.notify('User deleted', 'success')
      $scope.loadUsers(true);
    });
  }

  var editUserDialog = modalDialogService.registerDialog('#manage-users-edit-dialog')

  $scope.loadUsers()

})

app.service('manageUsersService', function(apiUsersService, notifyService) {
  var service = this

  function notifyResponseError(/*obj*/ response) {
    notifyService.notify('<strong>'+response.status+'</strong>', 'danger')
  }

  service.loadUsers = function(/*fn*/ callback, /*bool*/ force) {
    if (force || !service.cachedUsers) {
      apiUsersService.findUsers({}, function(/*[User]*/ users) {

        service.cachedUsers = users
        callback(service.cachedUsers)

      })
    } else {
      callback(service.cachedUsers)
    }
  }

})
