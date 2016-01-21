require 'spec_helper'

describe "Owner Resource" do
  include_context "standard"

  it "creates an owner" do
    user_client = Candlepin::BasicAuthClient.new
    key = rand_string('owner')
    res = user_client.create_owner(
      :owner => key,
      :display_name => key
    )
    expect(res).to be_success
  end

  it 'updates an owner' do
    old_name = owner[:displayName]
    res = owner_client.update_owner(
      :display_name => rand_string
    )
    expect(res).to be_success
    expect(res.content[:displayName]).to_not eq(old_name)
  end

  it "lists only requested consumer types" do
    owner_client.register(
      :username => owner_user[:username],
      :name => rand_string
    )
    res = owner_client.get_owner_consumers(:types => :system)
    expect(res).to be_success
    expect(res.content.length).to eq(1)

    res = owner_client.get_owner_consumers(:types => :candlepin)
    expect(res).to be_success
    expect(res.content).to be_empty
  end

  it "does not list another owner's consumers" do
    owner2 = new_owner
    owner2_user = new_owner_user(owner2)
    owner2_client = Candlepin::BasicAuthClient.new
    owner2_client.key = owner2[:key]

    owner2_client.register(
      :username => owner2_user[:username],
      :name => rand_string
    ).ok_content

    owner_consumer = owner_client.register(
      :username => owner_user[:username],
      :name => rand_string
    ).ok_content

    consumers = owner_client.get_owner_consumers.ok_content
    expect(consumers.length).to eq(1)
    expect(consumers.first[:name]).to eq(owner_consumer[:name])
  end
end
