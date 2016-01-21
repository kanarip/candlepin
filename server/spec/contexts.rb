require_relative '../client/ruby/candlepin'

shared_context "standard" do
  let!(:user_client) { Candlepin::BasicAuthClient.new }
  let!(:no_auth_client) { Candlepin::NoAuthClient.new }

  let(:owner) do
    new_owner
  end

  let(:owner_client) do
    client = Candlepin::BasicAuthClient.new
    client.key = owner[:key]
    client
  end

  let(:owner_user) do
    new_owner_user(owner)
  end

  let(:role) do
    res = user_client.create_role(
      :name => rand_string('role'),
    )
    raise "Could not create role for test" unless res.ok?
    res.content
  end

  let(:content) do
    res = user_client.create_owner_content(
      :content_id => "hello",
      :name => "Hello",
      :label => "hello",
      :owner => owner[:key],
    )
    raise "Could not create content for test" unless res.ok?
    res.content
  end

  let(:product) do
    p = rand_string('product')
    res = user_client.create_product(
      :product_id => p,
      :name => "Product #{p}",
      :owner => owner[:key],
    )
    raise "Could not create product for test" unless res.ok?
    res.content
  end
end
