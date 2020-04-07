import boto3
ddb = boto3.client('dynamodb', endpoint_url='http://localhost:8043/')
response = ddb.list_tables()
print(response)
